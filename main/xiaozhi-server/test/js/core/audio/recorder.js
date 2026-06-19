// Audio recording module
import { log } from '../../utils/logger.js?v=0143';
import { initOpusEncoder } from './opus-codec.js?v=0143';
import { getAudioPlayer } from './player.js?v=0143';

const TARGET_SAMPLE_RATE = 16000;
const WORKLET_PROCESSOR_NAME = 'xiaozhi-audio-recorder-v1';
let workletModulePromise = null;

export class AudioRecorder {
    constructor() {
        this.isRecording = false;
        this.audioContext = null;
        this.analyser = null;
        this.audioProcessor = null;
        this.audioProcessorType = null;
        this.audioSource = null;
        this.mediaStream = null;
        this.opusEncoder = null;
        this.pcmDataBuffer = new Int16Array();
        this.totalAudioSize = 0;
        this.visualizationRequest = null;
        this.recordingTimer = null;
        this.websocket = null;
        this._opusPacketsSent = 0;
        this._pcmCallbacks = 0;
        this._lastRms = 0;
        this._statsTimer = null;
        this.onRecordingStart = null;
        this.onRecordingStop = null;
        this.onVisualizerUpdate = null;
    }

    setWebSocket(ws) {
        this.websocket = ws;
    }

    getAudioContext() {
        return getAudioPlayer().getAudioContext();
    }

    async resumeAudioContext() {
        this.audioContext = this.getAudioContext();
        if (this.audioContext.state === 'suspended') {
            await this.audioContext.resume();
        }
        return this.audioContext;
    }

    initEncoder() {
        if (!this.opusEncoder) {
            this.opusEncoder = initOpusEncoder();
        }
        return this.opusEncoder;
    }

    getAudioProcessorCode() {
        return `
            class AudioRecorderProcessor extends AudioWorkletProcessor {
                constructor() {
                    super();
                    this.frameSize = 960;
                    this.buffer = new Int16Array(this.frameSize);
                    this.bufferIndex = 0;
                    this.isRecording = false;
                    this.port.onmessage = (event) => {
                        if (event.data.command === 'start') {
                            this.isRecording = true;
                        } else if (event.data.command === 'stop') {
                            this.isRecording = false;
                            if (this.bufferIndex > 0) {
                                this.port.postMessage({
                                    type: 'buffer',
                                    buffer: this.buffer.slice(0, this.bufferIndex)
                                });
                                this.bufferIndex = 0;
                            }
                        }
                    };
                }
                process(inputs) {
                    if (!this.isRecording) return true;
                    const input = inputs[0] && inputs[0][0];
                    if (!input) return true;
                    for (let i = 0; i < input.length; i++) {
                        if (this.bufferIndex >= this.frameSize) {
                            this.port.postMessage({ type: 'buffer', buffer: this.buffer.slice(0) });
                            this.bufferIndex = 0;
                        }
                        this.buffer[this.bufferIndex++] = Math.max(
                            -32768,
                            Math.min(32767, Math.floor(input[i] * 32767))
                        );
                    }
                    return true;
                }
            }
            registerProcessor('${WORKLET_PROCESSOR_NAME}', AudioRecorderProcessor);
        `;
    }

    async loadWorkletModuleOnce() {
        if (!workletModulePromise) {
            workletModulePromise = (async () => {
                const blob = new Blob([this.getAudioProcessorCode()], { type: 'application/javascript' });
                const url = URL.createObjectURL(blob);
                try {
                    await this.audioContext.audioWorklet.addModule(url);
                } finally {
                    URL.revokeObjectURL(url);
                }
            })();
        }
        return workletModulePromise;
    }

    async createAudioProcessor() {
        this.audioContext = this.getAudioContext();
        try {
            if (this.audioContext.audioWorklet) {
                await this.loadWorkletModuleOnce();
                const node = new AudioWorkletNode(this.audioContext, WORKLET_PROCESSOR_NAME);
                node.port.onmessage = (event) => {
                    if (event.data.type === 'buffer') {
                        this.processPCMBuffer(event.data.buffer);
                    }
                };
                const silent = this.audioContext.createGain();
                silent.gain.value = 0;
                node.connect(silent);
                silent.connect(this.audioContext.destination);
                log('使用 AudioWorklet 录音', 'success');
                return { node, type: 'worklet' };
            }
        } catch (error) {
            log(`AudioWorklet 不可用: ${error.message}，回退 ScriptProcessor`, 'warning');
        }
        return this.createScriptProcessor();
    }

    createScriptProcessor() {
        const frameSize = 4096;
        const scriptProcessor = this.audioContext.createScriptProcessor(frameSize, 1, 1);
        scriptProcessor.onaudioprocess = (event) => {
            if (!this.isRecording) return;
            const input = event.inputBuffer.getChannelData(0);
            const buffer = new Int16Array(input.length);
            for (let i = 0; i < input.length; i++) {
                buffer[i] = Math.max(-32768, Math.min(32767, Math.floor(input[i] * 32767)));
            }
            this.processPCMBuffer(buffer);
        };
        const silent = this.audioContext.createGain();
        silent.gain.value = 0;
        scriptProcessor.connect(silent);
        silent.connect(this.audioContext.destination);
        log('使用 ScriptProcessor 录音', 'warning');
        return { node: scriptProcessor, type: 'processor' };
    }

    logMicTrack(stream) {
        const track = stream.getAudioTracks()[0];
        if (!track) {
            log('麦克风流无 audio track', 'error');
            return;
        }
        const settings = track.getSettings?.() || {};
        log(
            `麦克风: ${track.label || '未知'} enabled=${track.enabled} muted=${track.muted} rate=${settings.sampleRate || '?'}Hz`,
            'info'
        );
    }

    getAnalyserRms() {
        if (!this.analyser) return 0;
        const data = new Float32Array(this.analyser.fftSize);
        this.analyser.getFloatTimeDomainData(data);
        let sumSq = 0;
        for (let i = 0; i < data.length; i++) {
            sumSq += data[i] * data[i];
        }
        return Math.sqrt(sumSq / data.length);
    }

    async ensureMediaStream() {
        const track = this.mediaStream?.getAudioTracks()[0];
        if (track && track.readyState === 'live' && !track.muted) {
            return this.mediaStream;
        }
        if (this.mediaStream) {
            this.mediaStream.getTracks().forEach((t) => t.stop());
            this.mediaStream = null;
        }
        this.mediaStream = await navigator.mediaDevices.getUserMedia({
            audio: {
                echoCancellation: true,
                noiseSuppression: true,
                autoGainControl: false,
                sampleRate: TARGET_SAMPLE_RATE,
                channelCount: 1
            }
        });
        this.logMicTrack(this.mediaStream);
        return this.mediaStream;
    }

    processPCMBuffer(buffer) {
        if (!this.isRecording) return;
        this._pcmCallbacks += 1;
        let sumSq = 0;
        for (let i = 0; i < buffer.length; i++) {
            const s = buffer[i] / 32768;
            sumSq += s * s;
        }
        this._lastRms = Math.sqrt(sumSq / Math.max(buffer.length, 1));

        const ctxRate = this.audioContext?.sampleRate || TARGET_SAMPLE_RATE;
        if (ctxRate !== TARGET_SAMPLE_RATE) {
            const ratio = ctxRate / TARGET_SAMPLE_RATE;
            const newLen = Math.floor(buffer.length / ratio);
            const resampled = new Int16Array(newLen);
            for (let i = 0; i < newLen; i++) {
                resampled[i] = buffer[Math.floor(i * ratio)];
            }
            buffer = resampled;
        }

        const newBuffer = new Int16Array(this.pcmDataBuffer.length + buffer.length);
        newBuffer.set(this.pcmDataBuffer);
        newBuffer.set(buffer, this.pcmDataBuffer.length);
        this.pcmDataBuffer = newBuffer;

        const samplesPerFrame = 960;
        while (this.pcmDataBuffer.length >= samplesPerFrame) {
            const frameData = this.pcmDataBuffer.slice(0, samplesPerFrame);
            this.pcmDataBuffer = this.pcmDataBuffer.slice(samplesPerFrame);
            this.encodeAndSendOpus(frameData);
        }
    }

    encodeAndSendOpus(pcmData = null) {
        if (!this.opusEncoder) {
            log('Opus编码器未初始化', 'error');
            return;
        }
        try {
            if (pcmData) {
                const opusData = this.opusEncoder.encode(pcmData);
                if (opusData && opusData.length > 0) {
                    this.totalAudioSize += opusData.length;
                    if (this.websocket?.readyState === WebSocket.OPEN) {
                        this.websocket.send(opusData);
                        this._opusPacketsSent += 1;
                        if (this._opusPacketsSent === 1) {
                            log(`首包 opus 已发送(${opusData.length} bytes)`, 'success');
                        } else if (this._opusPacketsSent % 25 === 0) {
                            log(`opus 已发送 ${this._opusPacketsSent} 包`, 'debug');
                        }
                    }
                }
            } else if (this.pcmDataBuffer.length > 0) {
                const padded = new Int16Array(960);
                padded.set(this.pcmDataBuffer.slice(0, 960));
                this.pcmDataBuffer = new Int16Array(0);
                this.encodeAndSendOpus(padded);
            }
        } catch (error) {
            log(`Opus编码错误: ${error.message}`, 'error');
        }
    }

    async start() {
        if (this.isRecording) return true;
        try {
            const { getWebSocketHandler } = await import('../network/websocket.js?v=0143');
            const wsHandler = getWebSocketHandler();
            if (wsHandler?.isRemoteSpeaking && wsHandler.currentSessionId) {
                this.websocket?.send(JSON.stringify({
                    session_id: wsHandler.currentSessionId,
                    type: 'abort',
                    reason: 'wake_word_detected'
                }));
            }
            if (!this.initEncoder()) {
                return false;
            }
            if (!this.websocket || this.websocket.readyState !== WebSocket.OPEN) {
                log('WebSocket未连接，无法开始录音', 'error');
                return false;
            }

            await this.resumeAudioContext();
            await this.ensureMediaStream();

            if (this.audioProcessor) {
                if (this.audioProcessorType === 'worklet' && this.audioProcessor.port) {
                    this.audioProcessor.port.postMessage({ command: 'stop' });
                }
                this.audioProcessor.disconnect();
                this.audioProcessor = null;
                this.audioProcessorType = null;
            }
            if (this.audioSource) {
                this.audioSource.disconnect();
                this.audioSource = null;
            }

            const processorResult = await this.createAudioProcessor();
            if (!processorResult) {
                log('无法创建音频处理器', 'error');
                return false;
            }

            this.pcmDataBuffer = new Int16Array();
            this._opusPacketsSent = 0;
            this._pcmCallbacks = 0;
            this._lastRms = 0;

            this.audioProcessor = processorResult.node;
            this.audioProcessorType = processorResult.type;
            this.audioSource = this.audioContext.createMediaStreamSource(this.mediaStream);
            this.analyser = this.audioContext.createAnalyser();
            this.analyser.fftSize = 2048;
            this.audioSource.connect(this.audioProcessor);
            this.audioSource.connect(this.analyser);

            this.isRecording = true;
            if (this.audioProcessorType === 'worklet' && this.audioProcessor.port) {
                this.audioProcessor.port.postMessage({ command: 'start' });
            }

            this.websocket.send(JSON.stringify({ type: 'listen', state: 'start', mode: 'auto' }));
            log('已发送 listen/start', 'info');

            if (this._statsTimer) clearInterval(this._statsTimer);
            this._statsTimer = setInterval(() => {
                if (!this.isRecording) return;
                const analyserRms = this.getAnalyserRms();
                log(
                    `录音统计: opus=${this._opusPacketsSent}包 pcm=${this._pcmCallbacks} PCM_RMS=${this._lastRms.toFixed(4)} 分析器RMS=${analyserRms.toFixed(4)}`,
                    this._lastRms < 0.002 && analyserRms < 0.002 ? 'warning' : 'info'
                );
                if (this._lastRms < 0.002 && analyserRms < 0.002 && this._pcmCallbacks > 3) {
                    log('麦克风采到静音，请检查系统默认输入设备/浏览器麦克风权限', 'warning');
                }
            }, 2000);

            if (this.onRecordingStart) this.onRecordingStart(0);
            let seconds = 0;
            this.recordingTimer = setInterval(() => {
                seconds += 0.1;
                if (this.onRecordingStart) this.onRecordingStart(seconds);
            }, 100);

            log('已开始PCM直接录音', 'success');
            return true;
        } catch (error) {
            log(`录音启动失败: ${error.message}`, 'error');
            this.isRecording = false;
            return false;
        }
    }

    stop() {
        if (!this.isRecording) return false;
        try {
            this.isRecording = false;
            if (this.audioProcessor) {
                if (this.audioProcessorType === 'worklet' && this.audioProcessor.port) {
                    this.audioProcessor.port.postMessage({ command: 'stop' });
                }
                this.audioProcessor.disconnect();
                this.audioProcessor = null;
                this.audioProcessorType = null;
            }
            if (this.audioSource) {
                this.audioSource.disconnect();
                this.audioSource = null;
            }
            if (this.visualizationRequest) {
                cancelAnimationFrame(this.visualizationRequest);
                this.visualizationRequest = null;
            }
            if (this.recordingTimer) {
                clearInterval(this.recordingTimer);
                this.recordingTimer = null;
            }
            if (this._statsTimer) {
                clearInterval(this._statsTimer);
                this._statsTimer = null;
            }
            log(`录音结束统计: 共发送 opus ${this._opusPacketsSent} 包`, 'info');
            this.encodeAndSendOpus();
            if (this.websocket?.readyState === WebSocket.OPEN) {
                this.websocket.send(JSON.stringify({ type: 'listen', state: 'stop' }));
            }
            if (this.onRecordingStop) this.onRecordingStop();
            log('已停止PCM直接录音', 'success');
            return true;
        } catch (error) {
            log(`录音停止失败: ${error.message}`, 'error');
            return false;
        }
    }

    releaseMediaStream() {
        if (this.mediaStream) {
            this.mediaStream.getTracks().forEach((t) => t.stop());
            this.mediaStream = null;
        }
    }

    getAnalyser() {
        return this.analyser;
    }
}

let audioRecorderInstance = null;

export function getAudioRecorder() {
    if (!audioRecorderInstance) {
        audioRecorderInstance = new AudioRecorder();
    }
    return audioRecorderInstance;
}

export async function checkMicrophoneAvailability() {
    if (!navigator.mediaDevices?.getUserMedia) return false;
    try {
        await getAudioRecorder().ensureMediaStream();
        return true;
    } catch (error) {
        log(`麦克风不可用: ${error.message}`, 'warning');
        return false;
    }
}

export function isHttpNonLocalhost() {
    const { protocol, hostname } = window.location;
    if (protocol !== 'http:') return false;
    if (hostname === 'localhost' || hostname === '127.0.0.1') return false;
    if (hostname.startsWith('192.168.') || hostname.startsWith('10.') || hostname.startsWith('172.')) {
        return false;
    }
    return true;
}

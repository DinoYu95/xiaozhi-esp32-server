import { getServiceUrl } from '../api';
import RequestService from '../httpRequest';


export default {
    // 用户列表
    getUserList(params, callback) {
        const queryParams = new URLSearchParams({
            page: params.page,
            limit: params.limit,
            mobile: params.mobile
        }).toString();

        RequestService.sendRequest()
            .url(`${getServiceUrl()}/admin/users?${queryParams}`)
            .method('GET')
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .networkFail((err) => {
                console.error('请求失败:', err)
                RequestService.reAjaxFun(() => {
                    this.getUserList(callback)
                })
            }).send()
    },
    // 删除用户
    deleteUser(id, callback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/admin/users/${id}`)
            .method('DELETE')
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .networkFail((err) => {
                console.error('删除失败:', err)
                RequestService.reAjaxFun(() => {
                    this.deleteUser(id, callback)
                })
            }).send()
    },
    // 重置用户密码
    resetUserPassword(id, callback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/admin/users/${id}`)
            .method('PUT')
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .networkFail((err) => {
                console.error('重置密码失败:', err)
                RequestService.reAjaxFun(() => {
                    this.resetUserPassword(id, callback)
                })
            }).send()
    },
    // 获取参数列表
    getParamsList(params, callback) {
        const queryParams = new URLSearchParams({
            page: params.page,
            limit: params.limit,
            paramCode: params.paramCode || ''
        }).toString();

        RequestService.sendRequest()
            .url(`${getServiceUrl()}/admin/params/page?${queryParams}`)
            .method('GET')
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .networkFail((err) => {
                console.error('获取参数列表失败:', err)
                RequestService.reAjaxFun(() => {
                    this.getParamsList(params, callback)
                })
            }).send()
    },
    // 保存
    addParam(data, callback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/admin/params`)
            .method('POST')
            .data(data)
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .networkFail((err) => {
                console.error('添加参数失败:', err)
                RequestService.reAjaxFun(() => {
                    this.addParam(data, callback)
                })
            }).send()
    },
    // 修改
    updateParam(data, callback, failCallback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/admin/params`)
            .method('PUT')
            .data(data)
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .fail((err) => {
                RequestService.clearRequestTime()
                failCallback(err)
            })
            .networkFail((err) => {
                console.error('更新参数失败:', err)
                RequestService.reAjaxFun(() => {
                    this.updateParam(data, callback)
                })
            }).send()
    },
    // 删除
    deleteParam(ids, callback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/admin/params/delete`)
            .method('POST')
            .data(ids)
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res);
            })
            .networkFail((err) => {
                console.error('删除参数失败:', err)
                RequestService.reAjaxFun(() => {
                    this.deleteParam(ids, callback)
                })
            }).send()
    },
    // 获取ws服务端列表
    getWsServerList(params, callback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/admin/server/server-list`)
            .method('GET')
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .networkFail((err) => {
                console.error('获取ws服务端列表失败:', err)
                RequestService.reAjaxFun(() => {
                    this.getWsServerList(params, callback)
                })
            }).send();
    },
    // 发送ws服务器动作指令
    sendWsServerAction(data, callback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/admin/server/emit-action`)
            .method('POST')
            .data(data)
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .networkFail((err) => {
                RequestService.reAjaxFun(() => {
                    this.sendWsServerAction(data, callback)
                })
            }).send();
    },

    /** 儿童风险：全局配置 */
    getChildRiskConfig(callback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/admin/child-risk/config`)
            .method('GET')
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .networkFail((err) => {
                RequestService.reAjaxFun(() => {
                    this.getChildRiskConfig(callback)
                })
            }).send()
    },
    saveChildRiskConfig(data, callback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/admin/child-risk/config`)
            .method('PUT')
            .data(data)
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .networkFail((err) => {
                RequestService.reAjaxFun(() => {
                    this.saveChildRiskConfig(data, callback)
                })
            }).send()
    },
    getChildRiskEventPage(page, limit, callback) {
        const q = new URLSearchParams({
            page: page || 1,
            limit: limit || 20,
        }).toString()
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/admin/child-risk/event/page?${q}`)
            .method('GET')
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .networkFail((err) => {
                RequestService.reAjaxFun(() => {
                    this.getChildRiskEventPage(page, limit, callback)
                })
            }).send()
    },
    listChildRiskRules(callback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/admin/child-risk/rule/list`)
            .method('GET')
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .networkFail((err) => {
                RequestService.reAjaxFun(() => {
                    this.listChildRiskRules(callback)
                })
            }).send()
    },
    saveChildRiskRule(data, callback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/admin/child-risk/rule`)
            .method('POST')
            .data(data)
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .networkFail((err) => {
                RequestService.reAjaxFun(() => {
                    this.saveChildRiskRule(data, callback)
                })
            }).send()
    },
    deleteChildRiskRule(id, callback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/admin/child-risk/rule?id=${encodeURIComponent(id)}`)
            .method('DELETE')
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .networkFail((err) => {
                RequestService.reAjaxFun(() => {
                    this.deleteChildRiskRule(id, callback)
                })
            }).send()
    },
    listChildRiskEvaluators(callback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/admin/child-risk/evaluator/list`)
            .method('GET')
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .networkFail((err) => {
                RequestService.reAjaxFun(() => {
                    this.listChildRiskEvaluators(callback)
                })
            }).send()
    },
    listChildRiskDomains(callback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/admin/child-risk/domain/list`)
            .method('GET')
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .networkFail((err) => {
                RequestService.reAjaxFun(() => {
                    this.listChildRiskDomains(callback)
                })
            }).send()
    },
    saveChildRiskEvaluator(data, callback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/admin/child-risk/evaluator`)
            .method('POST')
            .data(data)
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .networkFail((err) => {
                RequestService.reAjaxFun(() => {
                    this.saveChildRiskEvaluator(data, callback)
                })
            }).send()
    },
    deleteChildRiskEvaluator(id, callback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/admin/child-risk/evaluator?id=${encodeURIComponent(id)}`)
            .method('DELETE')
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .networkFail((err) => {
                RequestService.reAjaxFun(() => {
                    this.deleteChildRiskEvaluator(id, callback)
                })
            }).send()
    },
    getBetaFeedbackPage(params, callback) {
        const q = new URLSearchParams()
        if (params.page) q.set('page', params.page)
        if (params.limit) q.set('limit', params.limit)
        if (params.status) q.set('status', params.status)
        if (params.category) q.set('category', params.category)
        if (params.blocking !== undefined && params.blocking !== '') q.set('blocking', params.blocking)
        if (params.parentUserId) q.set('parentUserId', params.parentUserId)
        if (params.feedbackNo) q.set('feedbackNo', params.feedbackNo)
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/admin/feedback/page?${q.toString()}`)
            .method('GET')
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .networkFail((err) => {
                RequestService.reAjaxFun(() => {
                    this.getBetaFeedbackPage(params, callback)
                })
            }).send()
    },
    getBetaFeedbackDetail(id, callback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/admin/feedback/${id}`)
            .method('GET')
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .networkFail((err) => {
                RequestService.reAjaxFun(() => {
                    this.getBetaFeedbackDetail(id, callback)
                })
            }).send()
    },
    updateBetaFeedbackStatus(id, data, callback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/admin/feedback/${id}/status`)
            .method('PUT')
            .data(data)
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .networkFail((err) => {
                RequestService.reAjaxFun(() => {
                    this.updateBetaFeedbackStatus(id, data, callback)
                })
            }).send()
    },
    updateBetaFeedbackNote(id, data, callback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/admin/feedback/${id}/note`)
            .method('PUT')
            .data(data)
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .networkFail((err) => {
                RequestService.reAjaxFun(() => {
                    this.updateBetaFeedbackNote(id, data, callback)
                })
            }).send()
    },
    setBetaTester(data, callback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/admin/feedback/beta-tester`)
            .method('PUT')
            .data(data)
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .networkFail((err) => {
                RequestService.reAjaxFun(() => {
                    this.setBetaTester(data, callback)
                })
            }).send()
    },

}

package xiaozhi.modules.learning.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import xiaozhi.modules.learning.entity.KgNodeEntity;

class LearningKgNodeTypeUtilTest {

    @Test
    void normalizeTeachingNodeType_mapsChineseKnowledgePoint() {
        assertEquals("SKILL", LearningKgNodeTypeUtil.normalizeTeachingNodeType("小知识点"));
        assertEquals("SKILL", LearningKgNodeTypeUtil.normalizeTeachingNodeType("知识点"));
    }

    @Test
    void isMasterySkill_acceptsLegacyTeachingTypes() {
        KgNodeEntity node = new KgNodeEntity();
        node.setNodeType("小知识点");
        assertTrue(LearningKgNodeTypeUtil.isMasterySkill(node));
        node.setNodeType("KNOWLEDGE_POINT");
        assertTrue(LearningKgNodeTypeUtil.isMasterySkill(node));
        node.setNodeType("MODULE");
        assertFalse(LearningKgNodeTypeUtil.isMasterySkill(node));
    }
}

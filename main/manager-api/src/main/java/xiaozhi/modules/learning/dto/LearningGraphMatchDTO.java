package xiaozhi.modules.learning.dto;

import lombok.Data;

@Data
public class LearningGraphMatchDTO {
    private String subject;
    private String provinceCode;
    private String textbookEdition;
    /** 要查看的图谱年级（≤ 孩子 currentGrade） */
    private int graphGrade;
    private Integer childMaxGrade;
}

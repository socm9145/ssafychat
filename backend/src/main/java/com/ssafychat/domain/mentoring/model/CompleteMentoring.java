package com.ssafychat.domain.mentoring.model;

import com.ssafychat.domain.member.model.Member;
import lombok.*;

import javax.persistence.*;
import java.util.Date;

@Getter
@Setter
@ToString
@AllArgsConstructor
@RequiredArgsConstructor
@Builder
@Entity
public class CompleteMentoring {

    @Id
    private int completeMentoringId;

    @ManyToOne
    @JoinColumn(name = "mentee_uid", nullable = false)
    private Member mentee;

    @ManyToOne
    @JoinColumn(name = "mentor_uid", nullable = false)
    private Member mentor;

    @Column(nullable = false)
    private Date time;

    @Column(columnDefinition = "TEXT")
    private String chatLog;

    @Column(columnDefinition = "VARCHAR(20)", nullable = false)
    private String job;

    @Column(columnDefinition = "VARCHAR(30)", nullable = false)
    private String company;

    @Column(columnDefinition = "int default '3'")
    private int score;

    @Column(columnDefinition = "TEXT")
    private String reviewContent;

    @Column(columnDefinition = "int default '0'")
    private int reviewWidth;

    @Column(columnDefinition = "int default '0'")
    private int reviewHeight;

    @Column(columnDefinition = "BOOLEAN", nullable = false)
    private int reviewSelected;

    @Column(columnDefinition = "BOOLEAN", nullable = false)
    private int completed;

}

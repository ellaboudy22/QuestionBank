package com.questionbank.QuestionBank.repository;

import com.questionbank.QuestionBank.entity.Answer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

// Repository for managing answer data access
@Repository
public interface AnswerRepository extends JpaRepository<Answer, UUID> {

    List<Answer> findByQuestionIdAndIsActiveTrue(UUID questionId);

    Page<Answer> findByQuestionIdAndIsActiveTrue(UUID questionId, Pageable pageable);

    Page<Answer> findByQuestionIdAndIsCorrectTrueAndIsActiveTrue(UUID questionId, Pageable pageable);

    Page<Answer> findByQuestionIdAndIsCorrectFalseAndIsActiveTrue(UUID questionId, Pageable pageable);

    Page<Answer> findByIsCorrectTrue(Pageable pageable);

    Page<Answer> findByIsCorrectFalse(Pageable pageable);

    Page<Answer> findByIsActiveTrue(Pageable pageable);
}

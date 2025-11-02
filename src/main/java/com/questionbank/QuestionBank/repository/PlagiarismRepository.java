package com.questionbank.QuestionBank.repository;

import com.questionbank.QuestionBank.entity.Plagiarism;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

// Repository for managing plagiarism detection records
@Repository
public interface PlagiarismRepository extends JpaRepository<Plagiarism, UUID> {
    List<Plagiarism> findByUser(String user);
    List<Plagiarism> findByQuestionId(UUID questionId);
    List<Plagiarism> findByAnswerId(UUID answerId);
}

package com.questionbank.QuestionBank.repository;

import com.questionbank.QuestionBank.entity.Question;
import com.questionbank.QuestionBank.entity.QuestionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

// Repository for managing question data access
@Repository
public interface QuestionRepository extends JpaRepository<Question, UUID> {

    Page<Question> findByIsActiveTrue(Pageable pageable);

    List<Question> findByTypeAndIsActiveTrue(QuestionType type);

    List<Question> findByModuleAndIsActiveTrue(String module);

    List<Question> findByUnitAndIsActiveTrue(String unit);

    @Query("SELECT q FROM Question q WHERE " +
           "(:type IS NULL OR q.type = :type) AND " +
           "(:module IS NULL OR q.module = :module) AND " +
           "(:unit IS NULL OR q.unit = :unit) AND " +
           "(:title IS NULL OR LOWER(q.title) LIKE LOWER(CONCAT('%', :title, '%'))) AND " +
           "q.isActive = true")
    Page<Question> findWithFilters(@Param("type") QuestionType type,
                                 @Param("module") String module,
                                 @Param("unit") String unit,
                                 @Param("title") String title,
                                 Pageable pageable);

    @Query("SELECT q FROM Question q WHERE " +
           "(:type IS NULL OR q.type = :type) AND " +
           "(:module IS NULL OR q.module = :module) AND " +
           "(:unit IS NULL OR q.unit = :unit) AND " +
           "q.isActive = true " +
           "ORDER BY FUNCTION('RANDOM')")
    List<Question> findRandomWithFilters(@Param("type") QuestionType type,
                                       @Param("module") String module,
                                       @Param("unit") String unit);
}

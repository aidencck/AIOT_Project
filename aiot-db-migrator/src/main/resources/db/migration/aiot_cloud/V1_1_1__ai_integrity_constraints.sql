DELETE c
FROM ai_case_library c
LEFT JOIN ai_feedback_record f ON c.source_feedback_id = f.feedback_id
WHERE c.source_feedback_id IS NOT NULL
  AND f.feedback_id IS NULL;

DELETE f
FROM ai_feedback_record f
LEFT JOIN ai_diagnosis_record d ON f.diagnosis_id = d.diagnosis_id
WHERE d.diagnosis_id IS NULL;

ALTER TABLE ai_feedback_record
    ADD CONSTRAINT fk_ai_feedback_record_diagnosis_id
        FOREIGN KEY (diagnosis_id) REFERENCES ai_diagnosis_record(diagnosis_id);

ALTER TABLE ai_case_library
    ADD UNIQUE KEY uk_ai_case_library_source_feedback_id (source_feedback_id);

ALTER TABLE ai_case_library
    ADD CONSTRAINT fk_ai_case_library_source_feedback_id
        FOREIGN KEY (source_feedback_id) REFERENCES ai_feedback_record(feedback_id);

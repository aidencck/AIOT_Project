package com.aiot.common.dto.ai;

import lombok.Data;

import java.util.List;

@Data
public class AiKnowledgeImportRequest {
    private List<String> filePaths;
    private String source;
}

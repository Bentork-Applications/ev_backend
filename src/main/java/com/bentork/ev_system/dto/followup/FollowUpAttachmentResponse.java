package com.bentork.ev_system.dto.followup;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FollowUpAttachmentResponse {

    private Long id;
    private Long roundId;
    private String fileName;
    private String originalFileName;
    private String contentType;
    private Long fileSizeBytes;
    private LocalDateTime createdAt;
}


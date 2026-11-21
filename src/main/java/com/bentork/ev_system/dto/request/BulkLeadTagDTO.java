package com.bentork.ev_system.dto.request;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BulkLeadTagDTO {

    @JsonAlias({"ids", "selectedLeadIds", "leads", "leadIdList"})
    private List<Long> leadIds;

    @JsonAlias({"tagList", "tagNames"})
    private List<String> tags;

    @JsonAlias({"newTag", "label", "tagName"})
    private String tag;

    public List<String> getEffectiveTags() {
        if (tags != null && !tags.isEmpty()) {
            return tags;
        }
        if (tag != null && !tag.trim().isEmpty()) {
            List<String> list = new ArrayList<>();
            for (String t : tag.split(",")) {
                if (!t.trim().isEmpty()) {
                    list.add(t.trim());
                }
            }
            return list;
        }
        return new ArrayList<>();
    }
}

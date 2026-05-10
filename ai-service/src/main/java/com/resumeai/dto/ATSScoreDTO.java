package com.resumeai.dto;

import java.util.List;

import lombok.Data;

@Data
public class ATSScoreDTO {

	private String atsScore;
	
	private List<String> feedback;
	
	private List<String> suggestions;
}

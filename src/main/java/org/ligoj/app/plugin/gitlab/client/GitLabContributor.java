package org.ligoj.app.plugin.gitlab.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * GitLab contributor model.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitLabContributor {

	/**
	 * Contributor name.
	 */
	private String name;

	/**
	 * Contributor email.
	 */
	private String email;

	/**
	 * Commit count.
	 */
	private int commits;

}

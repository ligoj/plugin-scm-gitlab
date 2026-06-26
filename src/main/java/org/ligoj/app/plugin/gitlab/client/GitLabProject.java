package org.ligoj.app.plugin.gitlab.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Setter;

/**
 * GitLab project (repository) statistics model.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitLabProject {

	/**
	 * GitLab project identifier.
	 */
	private int id;

	/**
	 * Project name.
	 */
	private String name;

	/**
	 * Full namespace path, e.g. <code>group/sub/project</code>.
	 */
	@JsonProperty("path_with_namespace")
	private String pathWithNamespace;

	/**
	 * Open issues count.
	 */
	@JsonProperty("open_issues_count")
	private int openIssuesCount;

	/**
	 * Stars count.
	 */
	@JsonProperty("star_count")
	private int starCount;

	/**
	 * Forks count.
	 */
	@JsonProperty("forks_count")
	private int forksCount;

}

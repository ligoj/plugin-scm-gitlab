package org.ligoj.app.plugin.gitlab;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.ligoj.app.api.SubscriptionStatusWithData;
import org.ligoj.app.plugin.gitlab.client.GitLabContributor;
import org.ligoj.app.plugin.gitlab.client.GitLabProject;
import org.ligoj.app.plugin.scm.ScmResource;
import org.ligoj.app.plugin.scm.ScmServicePlugin;
import org.ligoj.app.resource.plugin.AbstractToolPluginResource;
import org.ligoj.bootstrap.core.NamedBean;
import org.ligoj.bootstrap.core.curl.CurlProcessor;
import org.ligoj.bootstrap.core.curl.CurlRequest;
import org.ligoj.bootstrap.core.json.InMemoryPagination;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * GitLab resource. Unlike GitHub, GitLab is self-hostable, so the API base
 * is derived from the node's <code>url</code> parameter
 * (<code>&lt;url&gt;/api/v4</code>) instead of a global configuration.
 */
@Path(GitlabPluginResource.URL)
@Component
@Produces(MediaType.APPLICATION_JSON)
public class GitlabPluginResource extends AbstractToolPluginResource implements ScmServicePlugin {

	/**
	 * Plug-in URL.
	 */
	public static final String URL = ScmResource.SERVICE_URL + "/gitlab";

	/**
	 * Plug-in key.
	 */
	public static final String KEY = URL.replace('/', ':').substring(1);

	/**
	 * GitLab base URL (node validation), e.g. <code>https://gitlab.com</code>.
	 */
	public static final String PARAMETER_URL = KEY + ":url";

	/**
	 * User or group namespace owning the project (node validation).
	 */
	public static final String PARAMETER_USER = KEY + ":user";

	/**
	 * Personal access token (node validation).
	 */
	public static final String PARAMETER_AUTH_KEY = KEY + ":auth-key";

	/**
	 * Target project/repository (subscription level).
	 */
	public static final String PARAMETER_REPO = KEY + ":repository";

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private InMemoryPagination inMemoryPagination;

	@Override
	public String getKey() {
		return KEY;
	}

	/**
	 * Return the GitLab API base URL for this node.
	 */
	private String getApiUrl(final Map<String, String> parameters) {
		return Strings.CS.removeEnd(parameters.get(PARAMETER_URL), "/") + "/api/v4";
	}

	/**
	 * Execute a GET request to GitLab with the private token authentication.
	 *
	 * @param request    The CURL request.
	 * @param parameters The node/subscription parameters.
	 * @return <code>true</code> when the request succeeds.
	 */
	private boolean processGitlabRequest(final CurlRequest request, final Map<String, String> parameters) {
		request.getHeaders().put("PRIVATE-TOKEN", parameters.get(PARAMETER_AUTH_KEY));
		try (var curl = new CurlProcessor()) {
			return curl.process(request);
		}
	}

	@Override
	public boolean checkStatus(final Map<String, String> parameters) {
		// Node validation: authenticated call to the current-user endpoint.
		return processGitlabRequest(new CurlRequest(HttpMethod.GET, getApiUrl(parameters) + "/user", null), parameters);
	}

	/**
	 * Validate the subscription repository (the GitLab project) and return it.
	 * Throws when the project cannot be resolved.
	 */
	private GitLabProject validateProject(final Map<String, String> parameters) throws IOException {
		final var user = parameters.get(PARAMETER_USER);
		final var repository = parameters.get(PARAMETER_REPO);
		final var request = new CurlRequest(HttpMethod.GET,
				getApiUrl(parameters) + "/projects?membership=true&search=" + repository, null);
		request.setSaveResponse(true);
		if (processGitlabRequest(request, parameters)) {
			final List<GitLabProject> projects = objectMapper.readValue(
					StringUtils.defaultIfBlank(request.getResponse(), "[]"), new TypeReference<List<GitLabProject>>() {
						// Nothing to extend
					});
			final var match = projects.stream()
					.filter(p -> (user + "/" + repository).equals(p.getPathWithNamespace())).findFirst();
			if (match.isPresent()) {
				return match.get();
			}
		}
		throw new ValidationJsonException(PARAMETER_REPO, "gitlab-repository", repository);
	}

	@Override
	public void link(final int subscription) throws IOException {
		validateProject(subscriptionResource.getParameters(subscription));
	}

	@Override
	public SubscriptionStatusWithData checkSubscriptionStatus(final Map<String, String> parameters) throws IOException {
		final var status = new SubscriptionStatusWithData();
		final var project = validateProject(parameters);
		status.put("issues", project.getOpenIssuesCount());
		status.put("stars", project.getStarCount());
		status.put("forks", project.getForksCount());
		status.put("contribs", getContributors(parameters, project.getId()));
		return status;
	}

	/**
	 * Return the contributors of the given project.
	 *
	 * @param parameters The node/subscription parameters.
	 * @param project    The GitLab numeric project identifier.
	 * @return The project contributors.
	 * @throws IOException When the GitLab response cannot be read.
	 */
	private List<GitLabContributor> getContributors(final Map<String, String> parameters, final int project)
			throws IOException {
		final var request = new CurlRequest(HttpMethod.GET,
				getApiUrl(parameters) + "/projects/" + project + "/repository/contributors", null);
		request.setSaveResponse(true);
		processGitlabRequest(request, parameters);
		return objectMapper.readValue(StringUtils.defaultIfBlank(request.getResponse(), "[]"),
				new TypeReference<List<GitLabContributor>>() {
					// Nothing to extend
				});
	}

	/**
	 * Return the GitLab projects matching the given name criteria.
	 *
	 * @param node     The node identifier holding the parameters.
	 * @param criteria The search criteria.
	 * @return The matching project names.
	 * @throws IOException When the GitLab response cannot be read.
	 */
	@GET
	@Path("repos/{node}/{criteria}")
	public List<NamedBean<String>> findReposByName(@PathParam("node") final String node,
			@PathParam("criteria") final String criteria) throws IOException {
		final var parameters = pvResource.getNodeParameters(node);
		final var request = new CurlRequest(HttpMethod.GET,
				getApiUrl(parameters) + "/projects?membership=true&search=" + criteria, null);
		request.setSaveResponse(true);
		if (processGitlabRequest(request, parameters)) {
			final List<GitLabProject> projects = objectMapper.readValue(
					StringUtils.defaultIfBlank(request.getResponse(), "[]"), new TypeReference<List<GitLabProject>>() {
						// Nothing to extend
					});
			return inMemoryPagination
					.newPage(projects.stream().map(p -> new NamedBean<>(p.getName(), p.getName())).toList(),
							PageRequest.of(0, 10))
					.getContent();
		}
		return Collections.emptyList();
	}

}

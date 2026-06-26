package org.ligoj.app.plugin.gitlab;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import jakarta.transaction.Transactional;

import org.apache.commons.io.IOUtils;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.ligoj.app.AbstractServerTest;
import org.ligoj.app.api.SubscriptionStatusWithData;
import org.ligoj.app.model.Node;
import org.ligoj.app.model.Parameter;
import org.ligoj.app.model.ParameterValue;
import org.ligoj.app.model.Project;
import org.ligoj.app.model.Subscription;
import org.ligoj.app.plugin.gitlab.client.GitLabContributor;
import org.ligoj.app.resource.subscription.SubscriptionResource;
import org.ligoj.bootstrap.MatcherUtil;
import org.ligoj.bootstrap.core.NamedBean;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Test class of {@link GitlabPluginResource}
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(locations = "classpath:/META-INF/spring/application-context-test.xml")
@Rollback
@Transactional
class GitlabPluginResourceTest extends AbstractServerTest {

	@Autowired
	private GitlabPluginResource resource;

	@Autowired
	private SubscriptionResource subscriptionResource;

	protected int subscription;

	@BeforeEach
	void prepareData() throws IOException {
		persistEntities("csv",
				new Class<?>[] { Node.class, Parameter.class, Project.class, Subscription.class, ParameterValue.class },
				StandardCharsets.UTF_8);
		this.subscription = getSubscription("Jupiter", GitlabPluginResource.KEY);

		// Coverage only
		Assertions.assertEquals("service:scm:gitlab", resource.getKey());
	}

	@Test
	void delete() throws Exception {
		resource.delete(subscription, false);
		em.flush();
		em.clear();
		// No custom data -> nothing to check
	}

	@Test
	void getVersion() throws Exception {
		Assertions.assertNull(resource.getVersion(subscription));
	}

	@Test
	void getLastVersion() throws Exception {
		Assertions.assertNull(resource.getLastVersion());
	}

	@Test
	void checkStatus() throws Exception {
		httpServer.stubFor(get(urlPathEqualTo("/api/v4/user"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody("{\"username\":\"junit\"}")));
		httpServer.start();
		Assertions.assertTrue(resource.checkStatus(subscriptionResource.getParametersNoCheck(subscription)));
	}

	@Test
	void checkStatusFailed() {
		httpServer.stubFor(
				get(urlPathEqualTo("/api/v4/user")).willReturn(aResponse().withStatus(HttpStatus.SC_UNAUTHORIZED)));
		httpServer.start();
		Assertions.assertFalse(resource.checkStatus(subscriptionResource.getParametersNoCheck(subscription)));
	}

	@Test
	void link() throws Exception {
		prepareMockProjects();
		resource.link(this.subscription);
		// Nothing to validate but the absence of exception
	}

	@Test
	void linkNotFound() {
		httpServer.stubFor(
				get(urlPathEqualTo("/api/v4/projects")).willReturn(aResponse().withStatus(HttpStatus.SC_NOT_FOUND)));
		httpServer.start();
		MatcherUtil.assertThrows(
				Assertions.assertThrows(ValidationJsonException.class, () -> resource.link(this.subscription)),
				"service:scm:gitlab:repository", "gitlab-repository");
	}

	@Test
	void linkNoMatch() {
		httpServer.stubFor(get(urlPathEqualTo("/api/v4/projects"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody("[]")));
		httpServer.start();
		MatcherUtil.assertThrows(
				Assertions.assertThrows(ValidationJsonException.class, () -> resource.link(this.subscription)),
				"service:scm:gitlab:repository", "gitlab-repository");
	}

	@SuppressWarnings("unchecked")
	@Test
	void checkSubscriptionStatus() throws IOException {
		prepareMockProjects();
		prepareMockContributors();
		final SubscriptionStatusWithData status = resource
				.checkSubscriptionStatus(subscriptionResource.getParametersNoCheck(subscription));
		Assertions.assertTrue(status.getStatus().isUp());
		Assertions.assertEquals(2, status.getData().get("issues"));
		Assertions.assertEquals(7, status.getData().get("stars"));
		Assertions.assertEquals(3, status.getData().get("forks"));
		final var contributors = (List<GitLabContributor>) status.getData().get("contribs");
		Assertions.assertEquals(3, contributors.size());
		Assertions.assertEquals("fabdouglas", contributors.getFirst().getName());
		Assertions.assertEquals(345, contributors.getFirst().getCommits());
	}

	@Test
	void findReposByName() throws IOException {
		prepareMockProjects();
		final List<NamedBean<String>> projects = resource.findReposByName("service:scm:gitlab:dig", "ligoj");
		Assertions.assertEquals(2, projects.size());
		Assertions.assertEquals("ligoj-jupiter", projects.getFirst().getId());
		Assertions.assertEquals("ligoj-jupiter", projects.getFirst().getName());
	}

	@Test
	void findReposByNameNoListing() throws IOException {
		httpServer.start();
		final List<NamedBean<String>> projects = resource.findReposByName("service:scm:gitlab:dig", "none");
		Assertions.assertEquals(0, projects.size());
	}

	private void prepareMockProjects() throws IOException {
		httpServer.stubFor(get(urlPathEqualTo("/api/v4/projects")).willReturn(aResponse()
				.withStatus(HttpStatus.SC_OK)
				.withBody(IOUtils.toString(
						new ClassPathResource("mock-server/scm/gitlab/projects.json").getInputStream(),
						StandardCharsets.UTF_8))));
		httpServer.start();
	}

	private void prepareMockContributors() throws IOException {
		httpServer.stubFor(get(urlPathEqualTo("/api/v4/projects/12345/repository/contributors")).willReturn(aResponse()
				.withStatus(HttpStatus.SC_OK)
				.withBody(IOUtils.toString(
						new ClassPathResource("mock-server/scm/gitlab/contributors.json").getInputStream(),
						StandardCharsets.UTF_8))));
		httpServer.start();
	}

}

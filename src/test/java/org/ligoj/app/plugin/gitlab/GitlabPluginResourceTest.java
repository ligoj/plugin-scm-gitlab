package org.ligoj.app.plugin.gitlab;

import java.time.Instant;
import org.ligoj.app.api.ToolPlugin;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import jakarta.transaction.Transactional;
import org.apache.commons.io.IOUtils;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.ligoj.app.AbstractServerTest;
import org.ligoj.app.api.SubscriptionStatusWithData;
import org.ligoj.app.model.*;
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

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
				new Class<?>[]{Node.class, Parameter.class, Project.class, Subscription.class, ParameterValue.class},
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
	void checkStatus() {
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
	void findReposByNameNoListing() {
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

	private static final String NODE = "service:scm:gitlab:dig";

	private void stubToken(final int status, final String body) {
		httpServer.stubFor(get(urlPathEqualTo("/api/v4/personal_access_tokens/self"))
				.willReturn(aResponse().withStatus(status).withBody(body)));
		httpServer.start();
	}

	private Instant getCredentialExpiry(final int status, final String body) {
		stubToken(status, body);
		return resource.getCredentialExpiry(NODE, subscriptionResource.getParametersNoCheck(subscription));
	}

	@Test
	void getCredentialExpiry() {
		// GitLab tokens stop at 00:00 UTC of their expiry day
		Assertions.assertEquals(Instant.parse("2026-11-30T00:00:00Z"), getCredentialExpiry(HttpStatus.SC_OK,
				"{\"id\":4,\"name\":\"ligoj\",\"active\":true,\"scopes\":[\"api\"],\"expires_at\":\"2026-11-30\"}"));
	}

	@Test
	void getCredentialExpiryNever() {
		Assertions.assertEquals(Instant.MAX, getCredentialExpiry(HttpStatus.SC_OK, "{\"id\":4,\"expires_at\":null}"));
	}

	@Test
	void getCredentialExpiryNotAvailable() {
		// A GitLab without this endpoint, or a token unable to read itself: unknown, without any warning nor error
		final var problems = new java.util.ArrayList<String>();
		final var context = (LoggerContext) LogManager.getContext(false);
		final var appender = new AbstractAppender("junit-expiry", null, null, true, Property.EMPTY_ARRAY) {
			@Override
			public void append(final LogEvent event) {
				if (event.getLevel().isMoreSpecificThan(Level.WARN)) {
					problems.add(event.getMessage().getFormattedMessage());
				}
			}
		};
		appender.start();
		context.getConfiguration().getRootLogger().addAppender(appender, Level.ALL, null);
		context.updateLoggers();
		try {
			Assertions.assertNull(getCredentialExpiry(HttpStatus.SC_NOT_FOUND, "{\"error\":\"404 Not Found\"}"));
			Assertions.assertNull(getCredentialExpiry(HttpStatus.SC_FORBIDDEN, "{\"message\":\"403 Forbidden\"}"));
		} finally {
			context.getConfiguration().getRootLogger().removeAppender("junit-expiry");
			context.updateLoggers();
		}
		Assertions.assertEquals(List.of(), problems);
	}

	@Test
	void getCredentialExpiryThroughApi() throws Exception {
		// Built against an API without this feature, the method overrides the API's one when it exists
		final var method = java.util.Arrays.stream(ToolPlugin.class.getMethods())
				.filter(m -> "getCredentialExpiry".equals(m.getName())).findFirst();
		Assumptions.assumeTrue(method.isPresent(), "API without the credential expiry");
		stubToken(HttpStatus.SC_OK, "{\"expires_at\":\"2026-11-30\"}");
		Assertions.assertEquals(Instant.parse("2026-11-30T00:00:00Z"),
				method.get().invoke(resource, NODE, subscriptionResource.getParametersNoCheck(subscription)));
	}
}

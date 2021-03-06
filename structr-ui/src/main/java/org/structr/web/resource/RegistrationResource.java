/*
 * Copyright (C) 2010-2021 Structr GmbH
 *
 * This file is part of Structr <http://structr.org>.
 *
 * Structr is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Structr is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Structr.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.structr.web.resource;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.structr.api.config.Settings;
import org.structr.api.search.SortOrder;
import org.structr.api.util.ResultStream;
import org.structr.common.MailHelper;
import org.structr.common.SecurityContext;
import org.structr.common.error.FrameworkException;
import org.structr.core.app.App;
import org.structr.core.app.Query;
import org.structr.core.app.StructrApp;
import org.structr.core.auth.Authenticator;
import org.structr.core.entity.AbstractNode;
import org.structr.core.entity.MailTemplate;
import org.structr.core.entity.Person;
import org.structr.core.entity.Principal;
import org.structr.core.graph.NodeFactory;
import org.structr.core.graph.Tx;
import org.structr.core.property.PropertyKey;
import org.structr.core.property.PropertyMap;
import org.structr.rest.RestMethodResult;
import org.structr.rest.auth.AuthHelper;
import org.structr.rest.exception.NotAllowedException;
import org.structr.rest.resource.Resource;
import org.structr.schema.action.ActionContext;
import org.structr.web.entity.User;
import org.structr.web.servlet.HtmlServlet;

/**
 * A resource to register new users.
 */
public class RegistrationResource extends Resource {

	private static final Logger logger = LoggerFactory.getLogger(RegistrationResource.class.getName());

	private enum TemplateKey {
		// outdated, deprecated Template keys. will be supported for some time, but should not be used anymore
		SENDER_NAME,
		SENDER_ADDRESS,
		SUBJECT,
		TEXT_BODY,
		HTML_BODY,
		BASE_URL,
		TARGET_PAGE,
		ERROR_PAGE,
		CONFIRM_REGISTRATION_PAGE,
		CONFIRM_KEY_KEY,
		TARGET_PAGE_KEY,
		ERROR_PAGE_KEY,

		// unified, new Template keys. these should be the ones to be used
		CONFIRM_REGISTRATION_SENDER_NAME,
		CONFIRM_REGISTRATION_SENDER_ADDRESS,
		CONFIRM_REGISTRATION_SUBJECT,
		CONFIRM_REGISTRATION_TEXT_BODY,
		CONFIRM_REGISTRATION_HTML_BODY,
		CONFIRM_REGISTRATION_BASE_URL,
		CONFIRM_REGISTRATION_TARGET_PAGE,
		CONFIRM_REGISTRATION_ERROR_PAGE,
//		CONFIRM_REGISTRATION_PAGE,				// this key was named correctly and does not need special treatment below
		CONFIRM_REGISTRATION_CONFIRM_KEY_KEY,
		CONFIRM_REGISTRATION_TARGET_PAGE_KEY,
		CONFIRM_REGISTRATION_ERROR_PAGE_KEY
	}

	@Override
	public boolean checkAndConfigure(String part, SecurityContext securityContext, HttpServletRequest request) {

		this.securityContext = securityContext;

		return (getUriPart().equals(part));

	}

	@Override
	public ResultStream doGet(final SortOrder sortOrder, int pageSize, int page) throws FrameworkException {
		throw new NotAllowedException("GET not allowed on " + getResourceSignature());
	}

	@Override
	public RestMethodResult doPut(Map<String, Object> propertySet) throws FrameworkException {
		throw new NotAllowedException("PUT not allowed on " + getResourceSignature());
	}

	@Override
	public RestMethodResult doPost(Map<String, Object> propertySet) throws FrameworkException {

		boolean existingUser = false;

		if (propertySet.containsKey("eMail")) {

			final PropertyKey<String> confKeyKey = StructrApp.key(User.class, "confirmationKey");
			final PropertyKey<String> eMailKey   = StructrApp.key(User.class, "eMail");
			final String emailString             = (String) propertySet.get("eMail");

			if (StringUtils.isEmpty(emailString)) {
				throw new FrameworkException(422, "No e-mail address given.");
			}

			final String localeString = (String) propertySet.get("locale");
			final String confKey      = AuthHelper.getConfirmationKey();

			final App app = StructrApp.getInstance(securityContext);

			Principal user = null;

			try (final Tx tx = app.tx(true, true, true)) {

				user = StructrApp.getInstance().nodeQuery(User.class).and(eMailKey, emailString).getFirst();
				if (user != null) {

					// For existing users, update confirmation key
					user.setProperty(confKeyKey, confKey);

					existingUser = true;

				} else {

					final Authenticator auth = securityContext.getAuthenticator();
					user = createUser(securityContext, eMailKey, emailString, propertySet, Settings.RestUserAutocreate.getValue(), auth.getUserClass(), confKey);
				}

				tx.success();
			}

			if (user != null) {

				boolean mailSuccess = false;

				try (final Tx tx = app.tx(true, true, true)) {

					mailSuccess = sendInvitationLink(user, propertySet, confKey, localeString);

					tx.success();
				}

				if (!mailSuccess) {

					throw new FrameworkException(503, "Unable to send confirmation e-mail.");

				}

				// If we have just updated the confirmation key for an existing user,
				// return 200 to distinguish from new users
				if (existingUser) {

					// return 200 OK
					return new RestMethodResult(HttpServletResponse.SC_OK);

				} else {

					// return 201 Created
					return new RestMethodResult(HttpServletResponse.SC_CREATED);

				}

			} else {

				throw new FrameworkException(503, "Unable to create new user.");

			}

		} else {

			throw new FrameworkException(422, "No e-mail address given.");
		}
	}

	@Override
	public RestMethodResult doOptions() throws FrameworkException {
		throw new NotAllowedException("OPTIONS not allowed on " + getResourceSignature());
	}

	@Override
	public Resource tryCombineWith(Resource next) throws FrameworkException {

		return null;

	}

	private boolean sendInvitationLink(final Principal user, final Map<String, Object> propertySetFromUserPOST, final String confKey, final String localeString) {

		final PropertyKey<String> eMailKey       = StructrApp.key(User.class, "eMail");
		final Map<String, String> replacementMap = new HashMap();

		// Populate the replacement map with all POSTed values
		// WARNING! This is unchecked user input!!
		populateReplacementMap(replacementMap, propertySetFromUserPOST);

		final String userEmail = user.getProperty(eMailKey);

		replacementMap.put(toPlaceholder("eMail"), userEmail);
		replacementMap.put(toPlaceholder("link"),
			getTemplateTextWithDeprecatedFallback(TemplateKey.CONFIRM_REGISTRATION_BASE_URL, TemplateKey.BASE_URL, ActionContext.getBaseUrl(securityContext.getRequest()), localeString)
			      + getTemplateText(TemplateKey.CONFIRM_REGISTRATION_PAGE, HtmlServlet.CONFIRM_REGISTRATION_PAGE, localeString)
			+ "?" + getTemplateTextWithDeprecatedFallback(TemplateKey.CONFIRM_REGISTRATION_CONFIRM_KEY_KEY, TemplateKey.CONFIRM_KEY_KEY, HtmlServlet.CONFIRM_KEY_KEY, localeString) + "=" + confKey
			+ "&" + getTemplateTextWithDeprecatedFallback(TemplateKey.CONFIRM_REGISTRATION_TARGET_PAGE_KEY, TemplateKey.TARGET_PAGE_KEY, HtmlServlet.TARGET_PAGE_KEY, localeString) + "=" + getTemplateTextWithDeprecatedFallback(TemplateKey.CONFIRM_REGISTRATION_TARGET_PAGE, TemplateKey.TARGET_PAGE, "register_thanks", localeString)
			+ "&" + getTemplateTextWithDeprecatedFallback(TemplateKey.CONFIRM_REGISTRATION_ERROR_PAGE_KEY, TemplateKey.ERROR_PAGE_KEY, HtmlServlet.ERROR_PAGE_KEY, localeString)   + "=" + getTemplateTextWithDeprecatedFallback(TemplateKey.CONFIRM_REGISTRATION_ERROR_PAGE, TemplateKey.ERROR_PAGE, "register_error", localeString)
		);

		String textMailTemplate = getTemplateTextWithDeprecatedFallback(TemplateKey.CONFIRM_REGISTRATION_TEXT_BODY, TemplateKey.TEXT_BODY, "Go to ${link} to finalize registration.", localeString);
		String htmlMailTemplate = getTemplateTextWithDeprecatedFallback(TemplateKey.CONFIRM_REGISTRATION_HTML_BODY, TemplateKey.HTML_BODY, "<div>Click <a href='${link}'>here</a> to finalize registration.</div>", localeString);
		String textMailContent  = MailHelper.replacePlaceHoldersInTemplate(textMailTemplate, replacementMap);
		String htmlMailContent  = MailHelper.replacePlaceHoldersInTemplate(htmlMailTemplate, replacementMap);

		try {

			MailHelper.sendHtmlMail(
				getTemplateTextWithDeprecatedFallback(TemplateKey.CONFIRM_REGISTRATION_SENDER_ADDRESS, TemplateKey.SENDER_ADDRESS, "structr-mail-daemon@localhost", localeString),
				getTemplateTextWithDeprecatedFallback(TemplateKey.CONFIRM_REGISTRATION_SENDER_NAME, TemplateKey.SENDER_NAME, "Structr Mail Daemon", localeString),
				userEmail, "", null, null, null,
				getTemplateTextWithDeprecatedFallback(TemplateKey.CONFIRM_REGISTRATION_SUBJECT, TemplateKey.SUBJECT, "Welcome to Structr, please finalize registration", localeString),
				htmlMailContent, textMailContent);

		} catch (Exception e) {

			logger.error("Unable to send registration e-mail", e);
			return false;
		}

		return true;

	}

	private String getTemplateText(final TemplateKey key, final String defaultValue, final String localeString) {

		try {

			final Query<MailTemplate> query = StructrApp.getInstance().nodeQuery(MailTemplate.class).andName(key.name());

			if (localeString != null) {
				query.and("locale", localeString);
			}

			MailTemplate template = query.getFirst();
			if (template != null) {

				final String text = template.getProperty("text");
				return text != null ? text : defaultValue;

			} else {

				return defaultValue;
			}

		} catch (FrameworkException ex) {

			logger.warn("Could not get mail template for key " + key, ex);
		}

		return null;
	}

	private String getTemplateTextWithDeprecatedFallback(final TemplateKey defaultKey, final TemplateKey deprecatedFallbackKey, final String defaultValue, final String localeString) {

		try {

			final Query<MailTemplate> query = StructrApp.getInstance().nodeQuery(MailTemplate.class).andName(defaultKey.name());

			if (localeString != null) {
				query.and("locale", localeString);
			}

			MailTemplate template = query.getFirst();
			if (template != null) {

				final String text = template.getProperty("text");
				return text != null ? text : getTemplateText(deprecatedFallbackKey, defaultValue, localeString);

			} else {

				return getTemplateText(deprecatedFallbackKey, defaultValue, localeString);
			}

		} catch (FrameworkException ex) {

			logger.warn("Could not get mail template for key " + defaultKey, ex);
		}

		return null;
	}

	private static void populateReplacementMap(final Map<String, String> replacementMap, final Map<String, Object> props) {

		for (Entry<String, Object> entry : props.entrySet()) {

			replacementMap.put(toPlaceholder(entry.getKey()), entry.getValue().toString());

		}
	}

	private static String toPlaceholder(final String key) {

		return "${".concat(key).concat("}");
	}

	/**
	 * Create a new user.
	 *
	 * If a {@link Person} is found, convert that object to a {@link User} object.
	 * Do not auto-create a new user.
	 *
	 * @param securityContext
	 * @param credentialKey
	 * @param credentialValue
	 * @param confKey
	 * @return user
	 */
	public Principal createUser(final SecurityContext securityContext, final PropertyKey credentialKey, final String credentialValue, final String confKey) throws FrameworkException {

		return createUser(securityContext, credentialKey, credentialValue, Collections.EMPTY_MAP, confKey);
	}

	/**
	 * Create a new user.
	 *
	 * If a {@link Person} is found, convert that object to a {@link User} object.
	 * Do not auto-create a new user.
	 *
	 * @param securityContext
	 * @param credentialKey
	 * @param credentialValue
	 * @param propertySet
	 * @param confKey
	 * @return user
	 */
	public Principal createUser(final SecurityContext securityContext, final PropertyKey credentialKey, final String credentialValue, final Map<String, Object> propertySet, final String confKey) throws FrameworkException {

		return createUser(securityContext, credentialKey, credentialValue, propertySet, false, confKey);
	}

	/**
	 * Create a new user.
	 *
	 * If a {@link Person} is found, convert that object to a {@link User} object.
	 * Do not auto-create a new user.
	 *
	 * @param securityContext
	 * @param credentialKey
	 * @param credentialValue
	 * @param autoCreate
	 * @param confKey
	 * @return user
	 */
	public Principal createUser(final SecurityContext securityContext, final PropertyKey credentialKey, final String credentialValue, final boolean autoCreate, final String confKey) throws FrameworkException {

		return createUser(securityContext, credentialKey, credentialValue, Collections.EMPTY_MAP, autoCreate, confKey);
	}

	/**
	 * Create a new user.
	 *
	 * If a {@link Person} is found, convert that object to a {@link User} object.
	 * Do not auto-create a new user.
	 *
	 * @param securityContext
	 * @param credentialKey
	 * @param credentialValue
	 * @param autoCreate
	 * @param userClass
	 * @param confKey
	 * @return user
	 */
	public static Principal createUser(final SecurityContext securityContext, final PropertyKey credentialKey, final String credentialValue, final boolean autoCreate, final Class userClass, final String confKey) throws FrameworkException {

		return createUser(securityContext, credentialKey, credentialValue, Collections.EMPTY_MAP, autoCreate, userClass, confKey);
	}

	/**
	 * Create a new user.
	 *
	 * If a {@link Person} is found, convert that object to a {@link User} object.
	 * If autoCreate is true, auto-create a new user, even if no matching person is found.
	 *
	 * @param securityContext
	 * @param credentialKey
	 * @param credentialValue
	 * @param propertySet
	 * @param autoCreate
	 * @param confKey
	 * @return user
	 */
	public Principal createUser(final SecurityContext securityContext, final PropertyKey credentialKey, final String credentialValue, final Map<String, Object> propertySet, final boolean autoCreate, final String confKey) throws FrameworkException {

		return createUser(securityContext, credentialKey, credentialValue, propertySet, autoCreate, User.class, confKey);
	}

	/**
	 * Create a new user.
	 *
	 * If a {@link Principal} is found, convert that object to a {@link Principal} object.
	 * If autoCreate is true, auto-create a new user, even if no matching person is found.
	 *
	 * @param securityContext
	 * @param credentialKey
	 * @param credentialValue
	 * @param propertySet
	 * @param autoCreate
	 * @param userClass
	 * @param confKey
	 * @return user
	 */
	public static Principal createUser(final SecurityContext securityContext, final PropertyKey credentialKey, final String credentialValue, final Map<String, Object> propertySet, final boolean autoCreate, final Class userClass, final String confKey) throws FrameworkException {

		final PropertyKey<String> confirmationKeyKey = StructrApp.key(User.class, "confirmationKey");
		Principal user = null;

		// First, search for a person with that e-mail address
		user = AuthHelper.getPrincipalForCredential(credentialKey, credentialValue);

		if (user != null) {

			user = new NodeFactory<Principal>(securityContext).instantiate(user.getNode());

			// convert to user
			user.unlockSystemPropertiesOnce();

			final PropertyMap changedProperties = new PropertyMap();
			changedProperties.put(AbstractNode.type, User.class.getSimpleName());
			changedProperties.put(confirmationKeyKey, confKey);
			user.setProperties(securityContext, changedProperties);

		} else if (autoCreate) {

			final App app = StructrApp.getInstance(securityContext);

			// Clear properties set by us from the user-defined props
			propertySet.remove(credentialKey.jsonName());
			propertySet.remove("confirmationKey");

			PropertyMap props = PropertyMap.inputTypeToJavaType(securityContext, StructrApp.getConfiguration().getNodeEntityClass("Principal"), propertySet);

			// Remove any property which is not included in configuration
			// eMail is mandatory and necessary
			final String customAttributesString = "eMail" + "," + Settings.RegistrationCustomAttributes.getValue();
			final List<String> customAttributes = Arrays.asList(customAttributesString.split("[ ,]+"));

			final Set<PropertyKey> propsToRemove = new HashSet<>();
			for (final PropertyKey key : props.keySet()) {
				if (!customAttributes.contains(key.jsonName())) {
					propsToRemove.add(key);
				}
			}

			for (final PropertyKey propToRemove : propsToRemove) {
				props.remove(propToRemove);
			}

			props.put(credentialKey, credentialValue);
			props.put(confirmationKeyKey, confKey);

			user = (Principal) app.create(userClass, props);

		} else {

			throw new FrameworkException(503, "User self-registration is not configured correctly.");
		}

		return user;
	}

	@Override
	public Class getEntityClass() {
		return null;
	}

	@Override
	public String getUriPart() {
		return "registration";
	}

	@Override
	public String getResourceSignature() {
		return "_registration";
	}

	@Override
	public boolean isCollectionResource() {
		return false;
	}

	@Override
	public boolean createPostTransaction() {
		return false;
	}
}

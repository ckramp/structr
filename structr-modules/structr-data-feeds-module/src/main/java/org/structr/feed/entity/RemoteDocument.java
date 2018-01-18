/**
 * Copyright (C) 2010-2018 Structr GmbH
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
package org.structr.feed.entity;

import java.io.InputStream;
import java.net.URI;
import org.apache.commons.lang3.StringUtils;
import org.structr.common.PropertyView;
import org.structr.common.fulltext.Indexable;
import org.structr.core.graph.NodeInterface;
import org.structr.rest.common.HttpHelper;
import org.structr.schema.SchemaService;
import org.structr.schema.json.JsonObjectType;
import org.structr.schema.json.JsonSchema;
import org.structr.feed.entity.feed.FeedItemContent;

/**
 *
 *
 */
public interface RemoteDocument extends NodeInterface, Indexable {

	static class Impl { static {

		final JsonSchema schema   = SchemaService.getDynamicSchema();
		final JsonObjectType type = schema.addType("RemoteDocument");

		type.setImplements(URI.create("https://structr.org/v1.1/definitions/RemoteDocument"));
		type.setImplements(URI.create("#/definitions/Indexable"));

		type.addStringProperty("url",              PropertyView.Public);
		type.addLongProperty("checksum",           PropertyView.Public).setReadOnly(true);
		type.addIntegerProperty("cacheForSeconds", PropertyView.Public);
		type.addIntegerProperty("version",         PropertyView.Public).setIndexed(true).setReadOnly(true);

		type.addPropertyGetter("url",              String.class);
		type.addPropertyGetter("contentType",      String.class);
		type.addPropertyGetter("extractedContent", String.class);

		// methods shared with FeedItemContent
		type.overrideMethod("afterCreation",    false,             FeedItemContent.class.getName() + ".updateIndex(this, arg0);");
		type.overrideMethod("getSearchContext", false, "return " + FeedItemContent.class.getName() + ".getSearchContext(this, arg0, arg1);").setDoExport(true);

		type.overrideMethod("getInputStream",   false, "return " + RemoteDocument.class.getName() + ".getInputStream(this);");
	}}

	String getUrl();

	static InputStream getInputStream(final RemoteDocument thisDocument) {

		final String remoteUrl = thisDocument.getUrl();
		if (StringUtils.isNotBlank(remoteUrl)) {

			return HttpHelper.getAsStream(remoteUrl);
		}

		return null;
	}
}

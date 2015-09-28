/*
  Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.

  The MySQL Connector/J is licensed under the terms of the GPLv2
  <http://www.gnu.org/licenses/old-licenses/gpl-2.0.html>, like most MySQL Connectors.
  There are special exceptions to the terms and conditions of the GPLv2 as it is applied to
  this software, see the FOSS License Exception
  <http://www.mysql.com/about/legal/licensing/foss-exception.html>.

  This program is free software; you can redistribute it and/or modify it under the terms
  of the GNU General Public License as published by the Free Software Foundation; version 2
  of the License.

  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  See the GNU General Public License for more details.

  You should have received a copy of the GNU General Public License along with this
  program; if not, write to the Free Software Foundation, Inc., 51 Franklin St, Fifth
  Floor, Boston, MA 02110-1301  USA

 */

package com.mysql.cj.mysqlx.devapi;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import com.mysql.cj.api.x.AddStatement;
import com.mysql.cj.api.x.Result;
import com.mysql.cj.core.exceptions.AssertionFailedException;
import com.mysql.cj.core.io.StatementExecuteOk;
import com.mysql.cj.x.json.JsonDoc;
import com.mysql.cj.x.json.JsonParser;
import com.mysql.cj.x.json.JsonString;

/**
 * @todo
 */
public class AddStatementImpl implements AddStatement {
    private CollectionImpl collection;
    private List<JsonDoc> newDocs;

    /* package private */AddStatementImpl(CollectionImpl collection, JsonDoc newDoc) {
        this.collection = collection;
        this.newDocs = new ArrayList<>();
        this.newDocs.add(newDoc);
    }

    /* package private */AddStatementImpl(CollectionImpl collection, JsonDoc[] newDocs) {
        this.collection = collection;
        this.newDocs = Arrays.asList(newDocs);
    }

    public AddStatement add(String jsonString) {
        try {
            JsonDoc doc = JsonParser.parseDoc(new StringReader(jsonString));
            return add(doc);
        } catch (IOException ex) {
            throw AssertionFailedException.shouldNotHappen(ex);
        }
    }

    public AddStatement add(JsonDoc doc) {
        this.newDocs.add(doc);
        return this;
    }

    public AddStatement add(JsonDoc[] docs) {
        this.newDocs.addAll(Arrays.asList(docs));
        return this;
    }

    private List<String> assignIds() {
        return this.newDocs.stream().filter(d -> d.get("_id") == null).map(d -> {
            String newId = UUID.randomUUID().toString().replaceAll("-", "");
            d.put("_id", new JsonString().setValue(newId));
            return newId;
        }).collect(Collectors.toList());
    }

    private List<String> serializeDocs() {
        return this.newDocs.stream().map(JsonDoc::toPackedString).collect(Collectors.toList());
    }

    public Result execute() {
        List<String> newIds = assignIds();
        StatementExecuteOk ok = this.collection.getSession().getMysqlxSession()
                .addDocs(this.collection.getSchema().getName(), this.collection.getName(), serializeDocs());
        // TODO allow more than one new assigned doc id
        return new UpdateResult(ok, newIds.size() > 0 ? newIds.get(0) : null);
    }

    public CompletableFuture<Result> executeAsync() {
        final List<String> newIds = assignIds();
        CompletableFuture<StatementExecuteOk> okF = this.collection.getSession().getMysqlxSession()
                .asyncAddDocs(this.collection.getSchema().getName(), this.collection.getName(), serializeDocs());
        // TODO allow more than one new assigned doc id
        return okF.thenApply(ok -> new UpdateResult(ok, newIds.size() > 0 ? newIds.get(0) : null));
    }
}

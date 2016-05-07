/**
 * Copyright (C) 2015 Zalando SE (http://tech.zalando.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.zalando.stups.swagger.codegen.language;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.swagger.codegen.CodegenModel;
import io.swagger.codegen.CodegenOperation;
import io.swagger.codegen.CodegenParameter;
import io.swagger.models.Model;
import joptsimple.internal.Strings;

/**
 * @author  Morten Haraldsen
 */
public class SpringServiceDelegatorNoSwaggerAnnotations extends AbstractSpringInterfaces {

    protected String sourceFolder = "";

    @Override
    public String getName() {
        return "springServiceDelegatorNoSwaggerAnnotations";
    }

    @Override
    public String getHelp() {
        return "Generates Spring MVC controllers with service-delegates";
    }

    public SpringServiceDelegatorNoSwaggerAnnotations() {
        super();
        embeddedTemplateDir = templateDir = "SpringServiceDelegatorNoSwaggerAnnotations";
        modelTemplateFiles.put("model.mustache", ".java");
        apiTemplateFiles.put("api.mustache", ".java");
        apiTemplateFiles.put("service.mustache", "Service.java");
    }

    @Override
    public CodegenModel fromModel(String name, Model model, Map<String, Model> allDefinitions) {
        final CodegenModel cgModel = super.fromModel(name, model, allDefinitions);
        // these are imports for the swagger annotations. We don't want those.
        cgModel.imports.remove("ApiModel");
        cgModel.imports.remove("ApiModelProperty");
        return cgModel;
    }

    @Override
    public boolean isBuilderSupported() {
        return true;
    }

    @Override
    public void enableBuilderSupport() {
        modelTemplateFiles.put("modelBuilder.mustache", "Builder.java");
    }

    @Override
    public boolean is303Supported() {
        return true;
    }

    @Override
    public void enable303() {
        modelTemplateFiles.remove("model.mustache");
        modelTemplateFiles.put("model303.mustache", ".java");
    }
    
	@SuppressWarnings("unchecked")
	@Override
	public Map<String, Object> postProcessOperations(Map<String, Object> objs)
	{
		final Set<String> permissionNames = new HashSet<>();
		final Set<String> roleNames = new HashSet<>();
		
		final Map<String, Object> operations = (Map<String, Object>) objs.get("operations");
        if (operations != null)
        {
            List<CodegenOperation> ops = (List<CodegenOperation>) operations.get("operation");
            for (CodegenOperation op : ops)
            {
            	final List<ObjectNode> extraArgs = (List<ObjectNode>) op.vendorExtensions.get("x-params");
            	if (extraArgs != null)
            	{
            		final List<CodegenParameter> extraParams = new LinkedList<>();
	            	for (ObjectNode extraArg : extraArgs)
	            	{
	            		final Entry<String, JsonNode> pair = extraArg.fields().next();
	            		final CodegenParameter param = new CodegenParameter();
	            		param.baseName = pair.getKey();
	            		param.dataType = pair.getValue().textValue();
	            		param.description = "Implementation specific parameter";
	            		extraParams.add(param);
	            	}
	            	op.vendorExtensions.put("x-params", extraParams);
            	}
            	
				final List<String> expressions = new LinkedList<>();

				// Role based security
				final List<String> roles = (List<String>) op.vendorExtensions.get("x-user-roles");
				if (roles != null)
				{
					for (String role : roles)
					{
						roleNames.add(role);
						expressions.add("hasRole('" + role + "')");
					}
				}
				
				// ACL security PRE
				final List<ObjectNode> aclDefs = (List<ObjectNode>) op.vendorExtensions.get("x-acl");
				if (aclDefs != null)
				{
					for (ObjectNode aclDef : aclDefs)
					{
						final String type = aclDef.get("type").textValue();
						final String expr = aclDef.get("expression").textValue();
						final String permission = aclDef.get("permission").textValue();
						expressions.add("hasPermission(" + expr + ", '" + type + "', '" + permission + "')");
						permissionNames.add(permission);
					}
				}

				if (! expressions.isEmpty())
				{
					final String secured = Strings.join(expressions, " && ");
					op.vendorExtensions.put("x-secured", secured);
				}
				
				// ACL security POST
				final Object postAclDefsObj = op.vendorExtensions.get("x-post-acl");
				if (postAclDefsObj != null)
				{
					final List<ObjectNode> postAclDefs = postAclDefsObj instanceof List ? (List<ObjectNode>) postAclDefsObj : Collections.singletonList((ObjectNode) postAclDefsObj);
					for (ObjectNode aclDef : postAclDefs)
					{
						final String permission = aclDef.get("permission").textValue();
						op.vendorExtensions.put("x-postAuthorize", "hasPermission(returnObject, '" + permission + "')");
					}
				}
			}
		}
		return super.postProcessOperations(objs);
	}
}

/* 
 * JBoss, Home of Professional Open Source 
 * Copyright 2011 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @author tags. All rights reserved. 
 * See the copyright.txt in the distribution for a 
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use, 
 * modify, copy, or redistribute it subject to the terms and conditions 
 * of the GNU Lesser General Public License, v. 2.1. 
 * This program is distributed in the hope that it will be useful, but WITHOUT A 
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A 
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details. 
 * You should have received a copy of the GNU Lesser General Public License, 
 * v.2.1 along with this distribution; if not, write to the Free Software 
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, 
 * MA  02110-1301, USA.
 */

package org.switchyard.tools.forge.rules;

import java.io.File;

import javax.inject.Inject;

import org.jboss.forge.parser.JavaParser;
import org.jboss.forge.parser.java.JavaInterface;
import org.jboss.forge.project.Project;
import org.jboss.forge.project.facets.JavaSourceFacet;
import org.jboss.forge.project.facets.MetadataFacet;
import org.jboss.forge.project.facets.ResourceFacet;
import org.jboss.forge.shell.PromptType;
import org.jboss.forge.shell.Shell;
import org.jboss.forge.shell.plugins.Alias;
import org.jboss.forge.shell.plugins.Command;
import org.jboss.forge.shell.plugins.Help;
import org.jboss.forge.shell.plugins.Option;
import org.jboss.forge.shell.plugins.PipeOut;
import org.jboss.forge.shell.plugins.Plugin;
import org.jboss.forge.shell.plugins.RequiresFacet;
import org.jboss.forge.shell.plugins.RequiresProject;
import org.jboss.forge.shell.plugins.Topic;
import org.switchyard.common.io.resource.ResourceType;
import org.switchyard.component.rules.common.RulesActionType;
import org.switchyard.component.rules.config.model.v1.V1RulesActionModel;
import org.switchyard.component.rules.config.model.v1.V1RulesComponentImplementationModel;
import org.switchyard.config.model.composite.JavaComponentServiceInterfaceModel;
import org.switchyard.config.model.composite.v1.V1ComponentModel;
import org.switchyard.config.model.composite.v1.V1ComponentServiceModel;
import org.switchyard.config.model.composite.v1.V1JavaComponentServiceInterfaceModel;
import org.switchyard.config.model.resource.v1.V1ResourceModel;
import org.switchyard.config.model.switchyard.SwitchYardModel;
import org.switchyard.tools.forge.plugin.SwitchYardFacet;
import org.switchyard.tools.forge.plugin.TemplateResource;

/**
 * Forge plugin for Rules component commands.
 */
@Alias("rules-service")
@RequiresProject
@RequiresFacet({RulesFacet.class, ResourceFacet.class})
@Topic("SOA")
@Help("Provides commands related to rules services in SwitchYard.")
public class RulesServicePlugin implements Plugin {

    // default to DRL as the resource type
    private static final String DEFAULT_RESOURCE_TYPE = "DRL";
    // process definition template
    private static final String RULES_TEMPLATE = "RulesTemplate.drl";
    // process definition file extension
    private static final String RULES_EXTENSION = ".drl";
    // process definition directory
    private static final String RULES_DIR = "META-INF";
    
    @Inject
    private Project _project;
    
    @Inject
    private Shell _shell;
    
    /**
     * Create a new rules service interface and implementation.
     * @param argServiceName service name
     * @param out shell output
     * @param argInterfaceClass class name of Java service interface
     * @param argRuleFilePath path to the rule definition
     * @param argRuleDefinitionType type of the rule definition
     * @param argIsStateful set to true to make the rule session stateful; default is false
     * @throws java.io.IOException error with file resources
     */
    @Command(value = "create", help = "Created a new service backed by a business rule.")
    public void newProcess(
            @Option(required = true,
                     name = "serviceName",
                     description = "The service name") 
             final String argServiceName,
             @Option(required = false,
                     name = "interfaceClass",
                     description = "The Java service interface") 
             final String argInterfaceClass,
             @Option(required = false,
                     name = "ruleDefinition",
                     description = "The business rule definition") 
             final String argRuleFilePath,
             @Option(required = false,
                 name = "ruleType",
                 description = "The type of rule definition (DRL, DSL, etc.") 
             final String argRuleDefinitionType,
             @Option(required = false,
                 name = "isStateful",
                 description = "Whether the rule session is stateful") 
             final Boolean argIsStateful,
             final PipeOut out)
    throws java.io.IOException {
      
        JavaSourceFacet java = _shell.getCurrentProject().getFacet(JavaSourceFacet.class);
        String pkgName = _project.getFacet(MetadataFacet.class).getTopLevelPackage();
        String interfaceClass = argInterfaceClass;
        
        if (interfaceClass == null) {
            // Figure out the Java package name for the interface
            if (pkgName == null) {
                pkgName = _shell.promptCommon(
                    "Java package for service interface:",
                    PromptType.JAVA_PACKAGE);
            }
        
            // Create the service interface
            JavaInterface ruleInterface = JavaParser.create(JavaInterface.class)
                .setPackage(pkgName)
                .setName(argServiceName)
                .setPublic();
            java.saveJavaSource(ruleInterface);
            interfaceClass = ruleInterface.getQualifiedName();

            out.println("Created service interface [" + interfaceClass + "]");
        }
        
        String ruleDefinitionType = argRuleDefinitionType;
        if (ruleDefinitionType == null) {
            ruleDefinitionType = DEFAULT_RESOURCE_TYPE;
        }
        
        String ruleDefinitionPath = argRuleFilePath;
        if (ruleDefinitionPath == null) {
            // Create an empty rule definition
            ruleDefinitionPath = RULES_DIR + File.separator + argServiceName + RULES_EXTENSION;
            TemplateResource template = new TemplateResource(RULES_TEMPLATE)
                .serviceName(argServiceName)
                .packageName(pkgName);
            template.writeResource(_project.getFacet(ResourceFacet.class).getResource(ruleDefinitionPath));
            
            out.println("Created rule definition [" + ruleDefinitionPath + "]");
        }
        
        // default is stateless
        boolean isStateful = argIsStateful == null ? false : argIsStateful;
        
        // Add the SwitchYard config
        createImplementationConfig(argServiceName, interfaceClass, ruleDefinitionPath, ruleDefinitionType, isStateful);
          
        // Notify user of success
        out.println("Rule service " + argServiceName + " has been created.");
    }
    
    private void createImplementationConfig(String serviceName,
            String interfaceName,
            String rulesDefinition,
            String rulesDefinitionType,
            boolean isStateful) {
        
        SwitchYardFacet switchYard = _project.getFacet(SwitchYardFacet.class);
        // Create the component service model
        V1ComponentModel component = new V1ComponentModel();
        component.setName(serviceName);
        V1ComponentServiceModel service = new V1ComponentServiceModel();
        service.setName(serviceName);
        JavaComponentServiceInterfaceModel csi = new V1JavaComponentServiceInterfaceModel();
        csi.setInterface(interfaceName);
        service.setInterface(csi);
        component.addService(service);
        
        // Create the Rules implementation model and add it to the component model
        V1RulesComponentImplementationModel rules = new V1RulesComponentImplementationModel();
        rules.addResource(new V1ResourceModel()
                .setLocation(rulesDefinition)
                .setType(ResourceType.valueOf(rulesDefinitionType)));
        rules.setStateful(isStateful);
        rules.addRulesAction(new V1RulesActionModel()
            .setName("operation")
            .setType(RulesActionType.EXECUTE_RULES));
        component.setImplementation(rules);
        
        // Add the new component service to the application config
        SwitchYardModel syConfig = switchYard.getSwitchYardConfig();
        syConfig.getComposite().addComponent(component);
        switchYard.saveConfig();
    }

}

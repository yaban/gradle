/*
 * Copyright 2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.api.internal.artifacts.ivyservice;

import org.gradle.api.artifacts.*;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.result.ResolutionResult;
import org.gradle.api.internal.artifacts.ArtifactDependencyResolver;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.ModuleInternal;
import org.gradle.api.internal.artifacts.ModuleMetadataProcessor;
import org.gradle.api.internal.artifacts.ResolverResults;
import org.gradle.api.internal.artifacts.component.DefaultModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.component.DefaultProjectComponentIdentifier;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.DefaultResolutionResultBuilder;
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository;
import org.gradle.api.specs.Spec;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class ShortcircuitEmptyConfigsArtifactDependencyResolver implements ArtifactDependencyResolver {
    private final ArtifactDependencyResolver dependencyResolver;

    public ShortcircuitEmptyConfigsArtifactDependencyResolver(ArtifactDependencyResolver dependencyResolver) {
        this.dependencyResolver = dependencyResolver;
    }

    public ResolverResults resolve(ConfigurationInternal configuration,
                                   List<? extends ResolutionAwareRepository> repositories,
                                   ModuleMetadataProcessor metadataProcessor) throws ResolveException {
        if (configuration.getAllDependencies().isEmpty()) {
            ModuleVersionIdentifier id = DefaultModuleVersionIdentifier.newId(configuration.getModule());
            ComponentIdentifier componentIdentifier = determineComponentIdentifier(id, configuration.getModule());
            ResolutionResult emptyResult = new DefaultResolutionResultBuilder().start(id, componentIdentifier).complete();
            return new ResolverResults(new EmptyResolvedConfiguration(), emptyResult);
        }
        return dependencyResolver.resolve(configuration, repositories, metadataProcessor);
    }

    private ComponentIdentifier determineComponentIdentifier(ModuleVersionIdentifier id, ModuleInternal moduleInternal) {
        String projectPath = moduleInternal.getProjectPath();
        return projectPath != null ? new DefaultProjectComponentIdentifier(projectPath) : new DefaultModuleComponentIdentifier(id.getGroup(), id.getName(), id.getVersion());
    }

    private static class EmptyResolvedConfiguration implements ResolvedConfiguration {

        public boolean hasError() {
            return false;
        }

        public LenientConfiguration getLenientConfiguration() {
            return new LenientConfiguration() {
                public Set<ResolvedDependency> getFirstLevelModuleDependencies(Spec<? super Dependency> dependencySpec) {
                    return Collections.emptySet();
                }

                public Set<UnresolvedDependency> getUnresolvedModuleDependencies() {
                    return Collections.emptySet();
                }

                public Set<File> getFiles(Spec<? super Dependency> dependencySpec) {
                    return Collections.emptySet();
                }

                public Set<ResolvedArtifact> getArtifacts(Spec<? super Dependency> dependencySpec) {
                    return Collections.emptySet();
                }
            };
        }

        public void rethrowFailure() throws ResolveException {
        }

        public Set<File> getFiles(Spec<? super Dependency> dependencySpec) {
            return Collections.emptySet();
        }

        public Set<ResolvedDependency> getFirstLevelModuleDependencies() {
            return Collections.emptySet();
        }

        public Set<ResolvedDependency> getFirstLevelModuleDependencies(Spec<? super Dependency> dependencySpec) throws ResolveException {
            return Collections.emptySet();
        }

        public Set<ResolvedArtifact> getResolvedArtifacts() {
            return Collections.emptySet();
        }
    }
}

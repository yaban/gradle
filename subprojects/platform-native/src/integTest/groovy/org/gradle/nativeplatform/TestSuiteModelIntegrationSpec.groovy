/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.nativeplatform

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.EnableModelDsl
import org.gradle.util.TextUtil

class TestSuiteModelIntegrationSpec extends AbstractIntegrationSpec {

    def "setup"() {
        EnableModelDsl.enable(executer)

        buildScript """
            import org.gradle.api.internal.rules.*
            import org.gradle.internal.service.*
            import org.gradle.api.internal.project.*
            import org.gradle.internal.reflect.*
            import org.gradle.model.internal.core.rule.describe.*
            import org.gradle.platform.base.internal.*
            import org.gradle.language.base.internal.*
            import org.gradle.api.internal.file.*

            apply type: NativeBinariesTestPlugin

            interface CustomTestSuite extends TestSuiteSpec {}
            class DefaultCustomTestSuite extends BaseComponentSpec implements CustomTestSuite {
                ComponentSpec testedComponent
            }

            interface CustomLanguageSourceSet extends LanguageSourceSet {
                String getData();
            }
            class DefaultCustomLanguageSourceSet extends BaseLanguageSourceSet implements CustomLanguageSourceSet {
                final String data = "foo"
            }

            class TestSuiteTypeRules extends RuleSource {
                @ComponentType
                public void registerCustomTestSuiteType(ComponentTypeBuilder<CustomTestSuite> builder) {
                    builder.defaultImplementation(DefaultCustomTestSuite)
                }

                @Defaults
                public void registerLanguageForTestSuite(TestSuiteContainer testSuites, ServiceRegistry serviceRegistry,
                                                         ProjectSourceSet projectSourceSet, ProjectIdentifier projectIdentifier) {
                    final Instantiator instantiator = serviceRegistry.get(Instantiator.class)
                    final FileResolver fileResolver = serviceRegistry.get(FileResolver.class)
                    testSuites.withType(CustomTestSuite).beforeEach { it ->
                        def suiteName = it.name
                        it.sources.registerFactory(CustomLanguageSourceSet.class, new NamedDomainObjectFactory<CustomLanguageSourceSet>() {
                            public CustomLanguageSourceSet create(String name) {
                                return BaseLanguageSourceSet.create(DefaultCustomLanguageSourceSet.class, name, suiteName, fileResolver, instantiator);
                            }
                        })
                    }
                }
            }

            apply type: TestSuiteTypeRules

            model {
                testSuites {
                    main(CustomTestSuite)
                }
            }
        """
    }

    void withMainSourceSet() {
        buildFile << """
            model {
                testSuites {
                    main {
                        sources {
                            main(CustomLanguageSourceSet)
                        }
                    }
                }
            }
        """
    }

    void withTestBinaryFactory() {
        buildFile << """
            import org.gradle.api.internal.project.taskfactory.ITaskFactory

            interface CustomTestBinary extends TestSuiteBinarySpec {
                String getData()
            }

            class DefaultCustomTestBinary extends BaseBinarySpec implements CustomTestBinary {
                TestSuiteSpec testSuite
                String data = "foo"
            }

            class TestBinaryTypeRules extends RuleSource {
                @BinaryType
                public void registerCustomTestBinaryFactory(BinaryTypeBuilder<CustomTestBinary> builder) {
                    builder.defaultImplementation(DefaultCustomTestBinary)
                }
            }

            apply type: TestBinaryTypeRules
        """
    }

    def "test suite sources and binaries containers are visible in model report"() {
        when:
        run "model"

        then:
        output.contains(TextUtil.toPlatformLineSeparators("""
    testSuites
        main
            binaries
            sources"""))
    }

    def "can reference sources container for a test suite in a rule"() {
        given:
        withMainSourceSet()
        buildFile << '''
            model {
                tasks {
                    create("printSourceNames") {
                        def sources = $("testSuites.main.sources")
                        doLast {
                            println "names: ${sources*.name}"
                        }
                    }
                }
            }
        '''

        when:
        succeeds "printSourceNames"

        then:
        output.contains "names: [main]"
    }

    def "test suite sources container elements are visible in model report"() {
        given:
        withMainSourceSet()
        buildFile << """
            model {
                testSuites {
                    main {
                        sources {
                            test(CustomLanguageSourceSet)
                        }
                    }
                    secondary(CustomTestSuite) {
                        sources {
                            test(CustomLanguageSourceSet)
                        }
                    }
                    foo(CustomTestSuite) {
                        sources {
                            bar(CustomLanguageSourceSet)
                        }
                    }
                }
            }
        """

        when:
        run "model"

        then:
        output.contains(TextUtil.toPlatformLineSeparators("""
    testSuites
        foo
            binaries
            sources
                bar = DefaultCustomLanguageSourceSet 'foo:bar'
        main
            binaries
            sources
                main = DefaultCustomLanguageSourceSet 'main:main'
                test = DefaultCustomLanguageSourceSet 'main:test'
        secondary
            binaries
            sources
                test = DefaultCustomLanguageSourceSet 'secondary:test'"""))
    }

    def "can reference sources container elements in a rule"() {
        given:
        withMainSourceSet()
        buildFile << '''
            model {
                tasks {
                    create("printSourceDisplayName") {
                        def sources = $("testSuites.main.sources.main")
                        doLast {
                            println "sources display name: ${sources.displayName}"
                        }
                    }
                }
            }
        '''

        when:
        succeeds "printSourceDisplayName"

        then:
        output.contains "sources display name: DefaultCustomLanguageSourceSet 'main:main'"
    }

    def "can reference sources container elements using specialized type in a rule"() {
        given:
        withMainSourceSet()
        buildFile << '''
            class TaskRules extends RuleSource {
                @Mutate
                void addPrintSourceDisplayNameTask(ModelMap<Task> tasks, @Path("testSuites.main.sources.main") CustomLanguageSourceSet sourceSet) {
                    tasks.create("printSourceData") {
                        doLast {
                            println "sources data: ${sourceSet.data}"
                        }
                    }
                }
            }

            apply type: TaskRules
        '''

        when:
        succeeds "printSourceData"

        then:
        output.contains "sources data: foo"
    }

    def "cannot remove source sets"() {
        given:
        withMainSourceSet()
        buildFile << '''
            class SourceSetRemovalRules extends RuleSource {
                @Mutate
                void clearSourceSets(@Path("testSuites.main.sources") NamedDomainObjectCollection<LanguageSourceSet> sourceSets) {
                    sourceSets.clear()
                }

                @Mutate
                void closeMainComponentSourceSetsForTasks(ModelMap<Task> tasks, @Path("testSuites.main.sources") NamedDomainObjectCollection<LanguageSourceSet> sourceSets) {
                }
            }

            apply type: SourceSetRemovalRules
        '''

        when:
        fails()

        then:
        failureHasCause("This collection does not support element removal.")
    }

    def "test suite binaries container elements and their tasks containers are visible in model report"() {
        given:
        withTestBinaryFactory()
        buildFile << '''
            model {
                testSuites {
                    main {
                        binaries {
                            first(CustomTestBinary)
                            second(CustomTestBinary)
                        }
                    }
                }
            }
        '''

        when:
        run "model"

        then:
        output.contains(TextUtil.toPlatformLineSeparators("""
    testSuites
        main
            binaries
                first
                    tasks = []
                second
                    tasks = []"""))
    }

    def "can reference binaries container for a test suite in a rule"() {
        given:
        withTestBinaryFactory()
        buildFile << '''
            model {
                testSuites {
                    main {
                        binaries {
                            first(CustomTestBinary)
                            second(CustomTestBinary)
                        }
                    }
                }
                tasks {
                    create("printBinaryNames") {
                        def binaries = $("testSuites.main.binaries")
                        doLast {
                            println "names: ${binaries.values().name}"
                        }
                    }
                }
            }
        '''

        when:
        succeeds "printBinaryNames"

        then:
        output.contains "names: [first, second]"
    }

    def "can reference binaries container elements using specialized type in a rule"() {
        given:
        withTestBinaryFactory()
        buildFile << '''
            model {
                testSuites {
                    main {
                        binaries {
                            main(CustomTestBinary)
                        }
                    }
                }
            }
            class TaskRules extends RuleSource {
                @Mutate
                void addPrintSourceDisplayNameTask(ModelMap<Task> tasks, @Path("testSuites.main.binaries.main") CustomTestBinary binary) {
                    tasks.create("printBinaryData") {
                        doLast {
                            println "binary data: ${binary.data}"
                        }
                    }
                }
            }

            apply type: TaskRules
        '''

        when:
        succeeds "printBinaryData"

        then:
        output.contains "binary data: foo"
    }

}

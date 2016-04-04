/* Licensed under the Apache License, Version 2.0 (the "License");
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
package org.camunda.bpm.engine.test.api.runtime.migration;

import static org.camunda.bpm.engine.test.api.runtime.migration.ModifiableBpmnModelInstance.modify;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.migration.MigrationPlan;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.camunda.bpm.engine.runtime.ActivityInstance;
import org.camunda.bpm.engine.runtime.Execution;
import org.camunda.bpm.engine.runtime.Job;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.runtime.VariableInstance;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.test.ProcessEngineRule;
import org.camunda.bpm.engine.test.api.runtime.migration.models.AsyncProcessModels;
import org.camunda.bpm.engine.test.api.runtime.migration.models.ProcessModels;
import org.camunda.bpm.engine.test.util.ExecutionTree;
import org.camunda.bpm.engine.variable.Variables;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

/**
 * @author Thorben Lindhauer
 *
 */
public class MigrationVariablesTest {

  protected ProcessEngineRule rule = new ProcessEngineRule(true);
  protected MigrationTestRule testHelper = new MigrationTestRule(rule);

  protected static final BpmnModelInstance ONE_BOUNDARY_TASK = ModifiableBpmnModelInstance.modify(ProcessModels.ONE_TASK_PROCESS)
      .activityBuilder("userTask")
      .boundaryEvent()
      .message("Message")
      .done();

  protected static final BpmnModelInstance CONCURRENT_BOUNDARY_TASKS = ModifiableBpmnModelInstance.modify(ProcessModels.PARALLEL_GATEWAY_PROCESS)
      .activityBuilder("userTask1")
      .boundaryEvent()
      .message("Message")
      .moveToActivity("userTask2")
      .boundaryEvent()
      .message("Message")
      .done();

  protected static final BpmnModelInstance SUBPROCESS_CONCURRENT_BOUNDARY_TASKS = ModifiableBpmnModelInstance.modify(ProcessModels.PARALLEL_GATEWAY_SUBPROCESS_PROCESS)
      .activityBuilder("userTask1")
      .boundaryEvent()
      .message("Message")
      .moveToActivity("userTask2")
      .boundaryEvent()
      .message("Message")
      .done();

  @Rule
  public RuleChain ruleChain = RuleChain.outerRule(rule).around(testHelper);

  protected RuntimeService runtimeService;
  protected TaskService taskService;

  @Before
  public void initServices() {
    runtimeService = rule.getRuntimeService();
    taskService = rule.getTaskService();
  }

  @Test
  public void testVariableAtScopeExecutionInScopeActivity() {
    // given
    ProcessDefinition sourceProcessDefinition = testHelper.deploy(ONE_BOUNDARY_TASK);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(ONE_BOUNDARY_TASK);

    MigrationPlan migrationPlan = rule.getRuntimeService().createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapEqualActivities()
      .build();

    ProcessInstance processInstance = runtimeService
        .startProcessInstanceById(sourceProcessDefinition.getId());
    ExecutionTree executionTreeBeforeMigration =
        ExecutionTree.forExecution(processInstance.getId(), rule.getProcessEngine());

    ExecutionTree scopeExecution = executionTreeBeforeMigration.getExecutions().get(0);

    runtimeService.setVariableLocal(scopeExecution.getId(), "foo", 42);

    // when
    testHelper.migrateProcessInstance(migrationPlan, processInstance);

    // then
    VariableInstance beforeMigration = testHelper.snapshotBeforeMigration.getSingleVariable("foo");

    Assert.assertEquals(1, testHelper.snapshotAfterMigration.getVariables().size());
    testHelper.assertVariableMigratedToExecution(beforeMigration, beforeMigration.getExecutionId());
  }

  @Test
  public void testVariableAtConcurrentExecutionInScopeActivity() {
    // given
    ProcessDefinition sourceProcessDefinition = testHelper.deploy(CONCURRENT_BOUNDARY_TASKS);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(CONCURRENT_BOUNDARY_TASKS);

    MigrationPlan migrationPlan = rule.getRuntimeService().createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapEqualActivities()
      .build();

    ProcessInstance processInstance = runtimeService
        .startProcessInstanceById(sourceProcessDefinition.getId());
    ExecutionTree executionTreeBeforeMigration =
        ExecutionTree.forExecution(processInstance.getId(), rule.getProcessEngine());

    ExecutionTree concurrentExecution = executionTreeBeforeMigration.getExecutions().get(0);

    runtimeService.setVariableLocal(concurrentExecution.getId(), "foo", 42);

    // when
    testHelper.migrateProcessInstance(migrationPlan, processInstance);

    // then
    VariableInstance beforeMigration = testHelper.snapshotBeforeMigration.getSingleVariable("foo");

    Assert.assertEquals(1, testHelper.snapshotAfterMigration.getVariables().size());
    testHelper.assertVariableMigratedToExecution(beforeMigration, beforeMigration.getExecutionId());
  }

  @Test
  public void testVariableAtScopeExecutionInNonScopeActivity() {
    // given
    ProcessDefinition sourceProcessDefinition = testHelper.deploy(ProcessModels.ONE_TASK_PROCESS);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(ProcessModels.ONE_TASK_PROCESS);

    MigrationPlan migrationPlan = rule.getRuntimeService().createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapEqualActivities()
      .build();

    ProcessInstance processInstance = runtimeService
        .startProcessInstanceById(sourceProcessDefinition.getId(), Variables.createVariables().putValue("foo", 42));

    // when
    testHelper.migrateProcessInstance(migrationPlan, processInstance);

    // then
    VariableInstance beforeMigration = testHelper.snapshotBeforeMigration.getSingleVariable("foo");

    Assert.assertEquals(1, testHelper.snapshotAfterMigration.getVariables().size());
    testHelper.assertVariableMigratedToExecution(beforeMigration, beforeMigration.getExecutionId());
  }

  @Test
  public void testVariableAtConcurrentExecutionInNonScopeActivity() {
    // given
    ProcessDefinition sourceProcessDefinition = testHelper.deploy(ProcessModels.PARALLEL_GATEWAY_PROCESS);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(ProcessModels.PARALLEL_GATEWAY_PROCESS);

    MigrationPlan migrationPlan = rule.getRuntimeService().createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapEqualActivities()
      .build();

    ProcessInstance processInstance = runtimeService
        .startProcessInstanceById(sourceProcessDefinition.getId());
    ExecutionTree executionTreeBeforeMigration =
        ExecutionTree.forExecution(processInstance.getId(), rule.getProcessEngine());

    ExecutionTree concurrentExecution = executionTreeBeforeMigration.getExecutions().get(0);

    runtimeService.setVariableLocal(concurrentExecution.getId(), "foo", 42);

    // when
    testHelper.migrateProcessInstance(migrationPlan, processInstance);

    // then
    VariableInstance beforeMigration = testHelper.snapshotBeforeMigration.getSingleVariable("foo");

    Assert.assertEquals(1, testHelper.snapshotAfterMigration.getVariables().size());
    testHelper.assertVariableMigratedToExecution(beforeMigration, beforeMigration.getExecutionId());
  }

  @Test
  public void testVariableAtConcurrentExecutionInScopeActivityAddParentScope() {
    // given
    ProcessDefinition sourceProcessDefinition = testHelper.deploy(CONCURRENT_BOUNDARY_TASKS);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(SUBPROCESS_CONCURRENT_BOUNDARY_TASKS);

    MigrationPlan migrationPlan = rule.getRuntimeService().createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("userTask1", "userTask1")
      .mapActivities("userTask2", "userTask2")
      .build();

    ProcessInstance processInstance = runtimeService
        .startProcessInstanceById(sourceProcessDefinition.getId());

    ExecutionTree executionTreeBeforeMigration =
        ExecutionTree.forExecution(processInstance.getId(), rule.getProcessEngine());

    ExecutionTree userTask1CCExecutionBefore  = executionTreeBeforeMigration
        .getLeafExecutions("userTask1")
        .get(0)
        .getParent();

    runtimeService.setVariableLocal(userTask1CCExecutionBefore.getId(), "foo", 42);

    // when
    testHelper.migrateProcessInstance(migrationPlan, processInstance);

    // then
    VariableInstance beforeMigration = testHelper.snapshotBeforeMigration.getSingleVariable("foo");

    ExecutionTree userTask1CCExecutionAfter  = testHelper.snapshotAfterMigration.getExecutionTree()
        .getLeafExecutions("userTask1")
        .get(0)
        .getParent();

    Assert.assertEquals(1, testHelper.snapshotAfterMigration.getVariables().size());
    ActivityInstance subProcessInstance = testHelper.getSingleActivityInstanceAfterMigration("subProcess");
    // for variables at concurrent executions that are parent of a leaf-scope-execution, the activity instance is
    // the activity instance id of the parent activity instance (which is probably a bug)
    testHelper.assertVariableMigratedToExecution(beforeMigration, userTask1CCExecutionAfter.getId(), subProcessInstance.getId());
  }

  @Test
  public void testVariableAtConcurrentExecutionInScopeActivityRemoveParentScope() {
    // given
    ProcessDefinition sourceProcessDefinition = testHelper.deploy(SUBPROCESS_CONCURRENT_BOUNDARY_TASKS);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(CONCURRENT_BOUNDARY_TASKS);

    MigrationPlan migrationPlan = rule.getRuntimeService().createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("userTask1", "userTask1")
      .mapActivities("userTask2", "userTask2")
      .build();

    ProcessInstance processInstance = runtimeService
        .startProcessInstanceById(sourceProcessDefinition.getId());

    ExecutionTree executionTreeBeforeMigration =
        ExecutionTree.forExecution(processInstance.getId(), rule.getProcessEngine());

    ExecutionTree userTask1CCExecutionBefore  = executionTreeBeforeMigration
        .getLeafExecutions("userTask1")
        .get(0)
        .getParent();

    runtimeService.setVariableLocal(userTask1CCExecutionBefore.getId(), "foo", 42);

    // when
    testHelper.migrateProcessInstance(migrationPlan, processInstance);

    // then
    VariableInstance beforeMigration = testHelper.snapshotBeforeMigration.getSingleVariable("foo");

    ExecutionTree userTask1CCExecutionAfter  = testHelper.snapshotAfterMigration.getExecutionTree()
        .getLeafExecutions("userTask1")
        .get(0)
        .getParent();

    Assert.assertEquals(1, testHelper.snapshotAfterMigration.getVariables().size());
    // for variables at concurrent executions that are parent of a leaf-scope-execution, the activity instance is
    // the activity instance id of the parent activity instance (which is probably a bug)
    testHelper.assertVariableMigratedToExecution(beforeMigration, userTask1CCExecutionAfter.getId(), processInstance.getId());
  }

  @Test
  public void testVariableAtConcurrentExecutionInNonScopeActivityAddParentScope() {
    // given
    ProcessDefinition sourceProcessDefinition = testHelper.deploy(ProcessModels.PARALLEL_GATEWAY_PROCESS);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(ProcessModels.PARALLEL_GATEWAY_SUBPROCESS_PROCESS);

    MigrationPlan migrationPlan = rule.getRuntimeService().createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("userTask1", "userTask1")
      .mapActivities("userTask2", "userTask2")
      .build();

    ProcessInstance processInstance = runtimeService
        .startProcessInstanceById(sourceProcessDefinition.getId());

    ExecutionTree executionTreeBeforeMigration =
        ExecutionTree.forExecution(processInstance.getId(), rule.getProcessEngine());

    ExecutionTree userTask1CCExecutionBefore  = executionTreeBeforeMigration
        .getLeafExecutions("userTask1")
        .get(0);

    runtimeService.setVariableLocal(userTask1CCExecutionBefore.getId(), "foo", 42);

    // when
    testHelper.migrateProcessInstance(migrationPlan, processInstance);

    // then
    VariableInstance beforeMigration = testHelper.snapshotBeforeMigration.getSingleVariable("foo");

    ExecutionTree userTask1CCExecutionAfter  = testHelper.snapshotAfterMigration.getExecutionTree()
        .getLeafExecutions("userTask1")
        .get(0);

    Assert.assertEquals(1, testHelper.snapshotAfterMigration.getVariables().size());
    testHelper.assertVariableMigratedToExecution(beforeMigration, userTask1CCExecutionAfter.getId());
  }

  @Test
  public void testVariableAtConcurrentExecutionInNonScopeActivityRemoveParentScope() {
    // given
    ProcessDefinition sourceProcessDefinition = testHelper.deploy(ProcessModels.PARALLEL_GATEWAY_SUBPROCESS_PROCESS);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(ProcessModels.PARALLEL_GATEWAY_PROCESS);

    MigrationPlan migrationPlan = rule.getRuntimeService().createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("userTask1", "userTask1")
      .mapActivities("userTask2", "userTask2")
      .build();

    ProcessInstance processInstance = runtimeService
        .startProcessInstanceById(sourceProcessDefinition.getId());

    ExecutionTree executionTreeBeforeMigration =
        ExecutionTree.forExecution(processInstance.getId(), rule.getProcessEngine());

    ExecutionTree userTask1CCExecutionBefore  = executionTreeBeforeMigration
        .getLeafExecutions("userTask1")
        .get(0);

    runtimeService.setVariableLocal(userTask1CCExecutionBefore.getId(), "foo", 42);

    // when
    testHelper.migrateProcessInstance(migrationPlan, processInstance);

    // then
    VariableInstance beforeMigration = testHelper.snapshotBeforeMigration.getSingleVariable("foo");

    ExecutionTree userTask1CCExecutionAfter  = testHelper.snapshotAfterMigration.getExecutionTree()
        .getLeafExecutions("userTask1")
        .get(0);

    Assert.assertEquals(1, testHelper.snapshotAfterMigration.getVariables().size());
    testHelper.assertVariableMigratedToExecution(beforeMigration, userTask1CCExecutionAfter.getId());
  }

  @Test
  public void testVariableAtScopeExecutionInScopeActivityAddParentScope() {
    // given
    ProcessDefinition sourceProcessDefinition = testHelper.deploy(ONE_BOUNDARY_TASK);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(SUBPROCESS_CONCURRENT_BOUNDARY_TASKS);

    MigrationPlan migrationPlan = rule.getRuntimeService().createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("userTask", "userTask1")
      .build();

    ProcessInstance processInstance = runtimeService
        .startProcessInstanceById(sourceProcessDefinition.getId());
    ExecutionTree executionTreeBeforeMigration =
        ExecutionTree.forExecution(processInstance.getId(), rule.getProcessEngine());

    ExecutionTree scopeExecution = executionTreeBeforeMigration.getExecutions().get(0);

    runtimeService.setVariableLocal(scopeExecution.getId(), "foo", 42);

    // when
    testHelper.migrateProcessInstance(migrationPlan, processInstance);

    // then
    VariableInstance beforeMigration = testHelper.snapshotBeforeMigration.getSingleVariable("foo");

    Assert.assertEquals(1, testHelper.snapshotAfterMigration.getVariables().size());
    testHelper.assertVariableMigratedToExecution(beforeMigration, beforeMigration.getExecutionId());
  }

  @Test
  public void testVariableAtTask() {
    // given
    ProcessDefinition sourceProcessDefinition = testHelper.deploy(ProcessModels.ONE_TASK_PROCESS);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(ProcessModels.ONE_TASK_PROCESS);

    MigrationPlan migrationPlan = rule.getRuntimeService().createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapEqualActivities()
      .build();

    ProcessInstance processInstance = runtimeService.startProcessInstanceById(sourceProcessDefinition.getId());

    Task task = taskService.createTaskQuery().singleResult();
    taskService.setVariableLocal(task.getId(), "foo", 42);

    // when
    testHelper.migrateProcessInstance(migrationPlan, processInstance);

    // then
    VariableInstance beforeMigration = testHelper.snapshotBeforeMigration.getSingleVariable("foo");

    Assert.assertEquals(1, testHelper.snapshotAfterMigration.getVariables().size());
    testHelper.assertVariableMigratedToExecution(beforeMigration, beforeMigration.getExecutionId());
  }

  @Test
  public void testVariableAtTaskAddParentScope() {
    // given
    ProcessDefinition sourceProcessDefinition = testHelper.deploy(ProcessModels.PARALLEL_GATEWAY_PROCESS);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(ProcessModels.PARALLEL_GATEWAY_SUBPROCESS_PROCESS);

    MigrationPlan migrationPlan = rule.getRuntimeService().createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("userTask1", "userTask1")
      .mapActivities("userTask2", "userTask2")
      .build();

    ProcessInstance processInstance = runtimeService.startProcessInstanceById(sourceProcessDefinition.getId());

    Task task = taskService.createTaskQuery().taskDefinitionKey("userTask1").singleResult();
    taskService.setVariableLocal(task.getId(), "foo", 42);

    // when
    testHelper.migrateProcessInstance(migrationPlan, processInstance);

    // then
    VariableInstance beforeMigration = testHelper.snapshotBeforeMigration.getSingleVariable("foo");

    ExecutionTree userTask1ExecutionAfter  = testHelper.snapshotAfterMigration.getExecutionTree()
        .getLeafExecutions("userTask1")
        .get(0);

    Assert.assertEquals(1, testHelper.snapshotAfterMigration.getVariables().size());
    testHelper.assertVariableMigratedToExecution(beforeMigration, userTask1ExecutionAfter.getId());
  }

  @Test
  public void testVariableAtTaskAndConcurrentExecutionAddParentScope() {
    // given
    ProcessDefinition sourceProcessDefinition = testHelper.deploy(ProcessModels.PARALLEL_GATEWAY_PROCESS);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(ProcessModels.PARALLEL_GATEWAY_SUBPROCESS_PROCESS);

    MigrationPlan migrationPlan = rule.getRuntimeService().createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("userTask1", "userTask1")
      .mapActivities("userTask2", "userTask2")
      .build();

    ProcessInstance processInstance = runtimeService.startProcessInstanceById(sourceProcessDefinition.getId());

    Task task = taskService.createTaskQuery().taskDefinitionKey("userTask1").singleResult();
    taskService.setVariableLocal(task.getId(), "foo", 42);
    runtimeService.setVariableLocal(task.getExecutionId(), "foo", 52);

    // when
    testHelper.migrateProcessInstance(migrationPlan, processInstance);

    // then
    VariableInstance taskVarBeforeMigration = testHelper.snapshotBeforeMigration.getSingleTaskVariable(task.getId(), "foo");

    ExecutionTree userTask1ExecutionAfter  = testHelper.snapshotAfterMigration.getExecutionTree()
        .getLeafExecutions("userTask1")
        .get(0);

    Assert.assertEquals(2, testHelper.snapshotAfterMigration.getVariables().size());
    testHelper.assertVariableMigratedToExecution(taskVarBeforeMigration, userTask1ExecutionAfter.getId());
  }

  @Test
  public void testVariableAtScopeExecutionBecomeNonScope() {
    // given
    ProcessDefinition sourceProcessDefinition = testHelper.deploy(ONE_BOUNDARY_TASK);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(ProcessModels.ONE_TASK_PROCESS);

    MigrationPlan migrationPlan = rule.getRuntimeService().createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapEqualActivities()
      .build();

    ProcessInstance processInstance = runtimeService
        .startProcessInstanceById(sourceProcessDefinition.getId());
    ExecutionTree executionTreeBeforeMigration =
        ExecutionTree.forExecution(processInstance.getId(), rule.getProcessEngine());

    ExecutionTree scopeExecution = executionTreeBeforeMigration.getExecutions().get(0);

    runtimeService.setVariableLocal(scopeExecution.getId(), "foo", 42);

    // when
    testHelper.migrateProcessInstance(migrationPlan, processInstance);

    // then
    VariableInstance beforeMigration = testHelper.snapshotBeforeMigration.getSingleVariable("foo");

    Assert.assertEquals(1, testHelper.snapshotAfterMigration.getVariables().size());
    testHelper.assertVariableMigratedToExecution(beforeMigration, processInstance.getId());

    // and the variable is concurrent local, i.e. expands on tree expansion
    runtimeService
      .createProcessInstanceModification(processInstance.getId())
      .startBeforeActivity("userTask")
      .execute();

    VariableInstance variableAfterExpansion = runtimeService.createVariableInstanceQuery().singleResult();
    Assert.assertNotNull(variableAfterExpansion);
    Assert.assertNotSame(processInstance.getId(), variableAfterExpansion.getExecutionId());

  }

  @Test
  public void testVariableAtConcurrentExecutionBecomeScope() {
    // given
    ProcessDefinition sourceProcessDefinition = testHelper.deploy(ProcessModels.PARALLEL_GATEWAY_PROCESS);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(ProcessModels.PARALLEL_SCOPE_TASKS);

    MigrationPlan migrationPlan = rule.getRuntimeService().createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapEqualActivities()
      .build();

    ProcessInstance processInstance = runtimeService
        .startProcessInstanceById(sourceProcessDefinition.getId());
    ExecutionTree executionTreeBeforeMigration =
        ExecutionTree.forExecution(processInstance.getId(), rule.getProcessEngine());

    ExecutionTree concurrentExecution = executionTreeBeforeMigration.getLeafExecutions("userTask1").get(0);

    runtimeService.setVariableLocal(concurrentExecution.getId(), "foo", 42);

    // when
    testHelper.migrateProcessInstance(migrationPlan, processInstance);

    // then
    VariableInstance beforeMigration = testHelper.snapshotBeforeMigration.getSingleVariable("foo");
    ExecutionTree userTask1CCExecution = testHelper.snapshotAfterMigration
      .getExecutionTree()
      .getLeafExecutions("userTask1")
      .get(0)
      .getParent();

    Assert.assertEquals(1, testHelper.snapshotAfterMigration.getVariables().size());
    testHelper.assertVariableMigratedToExecution(beforeMigration, userTask1CCExecution.getId());
  }

  @Test
  public void testVariableAtConcurrentAndScopeExecutionBecomeNonScope() {
    // given
    ProcessDefinition sourceProcessDefinition = testHelper.deploy(CONCURRENT_BOUNDARY_TASKS);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(ProcessModels.PARALLEL_GATEWAY_PROCESS);

    MigrationPlan migrationPlan = rule.getRuntimeService().createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapEqualActivities()
      .build();

    ProcessInstance processInstance = runtimeService
        .startProcessInstanceById(sourceProcessDefinition.getId());
    ExecutionTree executionTreeBeforeMigration =
        ExecutionTree.forExecution(processInstance.getId(), rule.getProcessEngine());

    ExecutionTree scopeExecution = executionTreeBeforeMigration.getLeafExecutions("userTask1").get(0);
    ExecutionTree concurrentExecution = scopeExecution.getParent();

    runtimeService.setVariableLocal(scopeExecution.getId(), "foo", 42);
    runtimeService.setVariableLocal(concurrentExecution.getId(), "foo", 42);

    // when
    try {
      testHelper.migrateProcessInstance(migrationPlan, processInstance);
      Assert.fail("expected exception");
    }
    catch (ProcessEngineException e) {
      Assert.assertThat(e.getMessage(), CoreMatchers.containsString("The variable 'foo' exists in both, this scope"
          + " and concurrent local in the parent scope. Migrating to a non-scope activity would overwrite one of them."));
    }
  }

  @Test
  public void testVariableAtParentScopeExecutionAndScopeExecutionBecomeNonScope() {
    // given
    ProcessDefinition sourceProcessDefinition = testHelper.deploy(ONE_BOUNDARY_TASK);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(ProcessModels.ONE_TASK_PROCESS);

    MigrationPlan migrationPlan = rule.getRuntimeService().createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapEqualActivities()
      .build();

    ProcessInstance processInstance = runtimeService
        .startProcessInstanceById(sourceProcessDefinition.getId());
    ExecutionTree executionTreeBeforeMigration =
        ExecutionTree.forExecution(processInstance.getId(), rule.getProcessEngine());

    ExecutionTree scopeExecution = executionTreeBeforeMigration.getLeafExecutions("userTask").get(0);

    runtimeService.setVariableLocal(scopeExecution.getId(), "foo", "userTaskScopeValue");
    runtimeService.setVariableLocal(processInstance.getId(), "foo", "processScopeValue");

    // when
    testHelper.migrateProcessInstance(migrationPlan, processInstance);

    // then the process scope variable was overwritten due to a compacted execution tree
    Assert.assertEquals(1, testHelper.snapshotAfterMigration.getVariables().size());

    VariableInstance variable = testHelper.snapshotAfterMigration.getVariables().iterator().next();

    Assert.assertEquals("userTaskScopeValue", variable.getValue());
  }

  @Test
  public void testVariableAtConcurrentExecutionAddParentScopeBecomeNonConcurrent() {
    // given
    ProcessDefinition sourceProcessDefinition = testHelper.deploy(ProcessModels.PARALLEL_GATEWAY_PROCESS);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(
        modify(ProcessModels.PARALLEL_TASK_AND_SUBPROCESS_PROCESS)
        .activityBuilder("subProcess")
        .camundaInputParameter("foo", "subProcessValue")
        .done());

    MigrationPlan migrationPlan = rule.getRuntimeService().createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("userTask1", "userTask1")
      .mapActivities("userTask2", "userTask2")
      .build();

    ProcessInstance processInstance = runtimeService
        .startProcessInstanceById(sourceProcessDefinition.getId());
    ExecutionTree executionTreeBeforeMigration =
        ExecutionTree.forExecution(processInstance.getId(), rule.getProcessEngine());

    ExecutionTree task1CcExecution = executionTreeBeforeMigration.getLeafExecutions("userTask1").get(0);
    ExecutionTree task2CcExecution = executionTreeBeforeMigration.getLeafExecutions("userTask2").get(0);

    runtimeService.setVariableLocal(task1CcExecution.getId(), "foo", "task1Value");
    runtimeService.setVariableLocal(task2CcExecution.getId(), "foo", "task2Value");

    // when
    testHelper.migrateProcessInstance(migrationPlan, processInstance);

    // then the io mapping variable was overwritten due to a compacted execution tree
    Assert.assertEquals(2, testHelper.snapshotAfterMigration.getVariables().size());

    List<String> values = new ArrayList<String>();
    for (VariableInstance variable : testHelper.snapshotAfterMigration.getVariables()) {
      values.add((String) variable.getValue());
    }

    Assert.assertTrue(values.contains("task1Value"));
    Assert.assertTrue(values.contains("task2Value"));
  }

  @Test
  public void testAddScopeWithInputMappingAndVariableOnConcurrentExecutions() {
    // given
    ProcessDefinition sourceProcessDefinition = testHelper.deploy(ProcessModels.PARALLEL_GATEWAY_PROCESS);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(
        modify(ProcessModels.PARALLEL_GATEWAY_SUBPROCESS_PROCESS)
          .activityBuilder("subProcess").camundaInputParameter("foo", "inputOutputValue").done()
      );

    MigrationPlan migrationPlan = rule.getRuntimeService().createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("userTask1", "userTask1")
      .mapActivities("userTask2", "userTask2")
      .build();

    ProcessInstance processInstance = runtimeService
        .startProcessInstanceById(sourceProcessDefinition.getId());

    ExecutionTree executionTreeBeforeMigration =
        ExecutionTree.forExecution(processInstance.getId(), rule.getProcessEngine());

    ExecutionTree userTask1CCExecutionBefore  = executionTreeBeforeMigration
        .getLeafExecutions("userTask1")
        .get(0);
    ExecutionTree userTask2CCExecutionBefore  = executionTreeBeforeMigration
        .getLeafExecutions("userTask2")
        .get(0);

    runtimeService.setVariableLocal(userTask1CCExecutionBefore.getId(), "foo", "customValue");
    runtimeService.setVariableLocal(userTask2CCExecutionBefore.getId(), "foo", "customValue");

    // when
    testHelper.migrateProcessInstance(migrationPlan, processInstance);

    // then the scope variable instance has been overwritten during compaction (conform to prior behavior);
    // although this is tested here, changing this behavior may be ok in the future
    Collection<VariableInstance> variables = testHelper.snapshotAfterMigration.getVariables();
    Assert.assertEquals(2, variables.size());

    for (VariableInstance variable : variables) {
      Assert.assertEquals("customValue", variable.getValue());
    }

    ExecutionTree subProcessExecution  = testHelper.snapshotAfterMigration.getExecutionTree()
        .getLeafExecutions("userTask2")
        .get(0)
        .getParent();

    Assert.assertNotNull(testHelper.snapshotAfterMigration.getSingleVariable(subProcessExecution.getId(), "foo"));
  }

  @Test
  public void testVariableAtScopeAndConcurrentExecutionAddParentScope() {
    // given
    ProcessDefinition sourceProcessDefinition = testHelper.deploy(ProcessModels.PARALLEL_GATEWAY_PROCESS);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(ProcessModels.PARALLEL_GATEWAY_SUBPROCESS_PROCESS);

    MigrationPlan migrationPlan = rule.getRuntimeService().createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("userTask1", "userTask1")
      .mapActivities("userTask2", "userTask2")
      .build();

    ProcessInstance processInstance = runtimeService
        .startProcessInstanceById(sourceProcessDefinition.getId());

    ExecutionTree executionTreeBeforeMigration =
        ExecutionTree.forExecution(processInstance.getId(), rule.getProcessEngine());

    ExecutionTree userTask1CCExecutionBefore  = executionTreeBeforeMigration
        .getLeafExecutions("userTask1")
        .get(0);
    ExecutionTree userTask2CCExecutionBefore  = executionTreeBeforeMigration
        .getLeafExecutions("userTask2")
        .get(0);

    runtimeService.setVariableLocal(processInstance.getId(), "foo", "processInstanceValue");
    runtimeService.setVariableLocal(userTask1CCExecutionBefore.getId(), "foo", "task1Value");
    runtimeService.setVariableLocal(userTask2CCExecutionBefore.getId(), "foo", "task2Value");

    VariableInstance processScopeVariable = runtimeService.createVariableInstanceQuery().variableValueEquals("foo", "processInstanceValue").singleResult();
    VariableInstance task1Variable = runtimeService.createVariableInstanceQuery().variableValueEquals("foo", "task1Value").singleResult();
    VariableInstance task2Variable = runtimeService.createVariableInstanceQuery().variableValueEquals("foo", "task2Value").singleResult();

    // when
    testHelper.migrateProcessInstance(migrationPlan, processInstance);

    // then the scope variable instance has been overwritten during compaction (conform to prior behavior);
    // although this is tested here, changing this behavior may be ok in the future
    Assert.assertEquals(3, testHelper.snapshotAfterMigration.getVariables().size());

    VariableInstance processScopeVariableAfterMigration = testHelper.snapshotAfterMigration.getVariable(processScopeVariable.getId());
    Assert.assertNotNull(processScopeVariableAfterMigration);
    Assert.assertEquals("processInstanceValue", processScopeVariableAfterMigration.getValue());

    VariableInstance task1VariableAfterMigration = testHelper.snapshotAfterMigration.getVariable(task1Variable.getId());
    Assert.assertNotNull(task1VariableAfterMigration);
    Assert.assertEquals("task1Value", task1VariableAfterMigration.getValue());

    VariableInstance task2VariableAfterMigration = testHelper.snapshotAfterMigration.getVariable(task2Variable.getId());
    Assert.assertNotNull(task2VariableAfterMigration);
    Assert.assertEquals("task2Value", task2VariableAfterMigration.getValue());

  }

  @Test
  public void testVariableAtScopeAndConcurrentExecutionRemoveParentScope() {
    // given
    ProcessDefinition sourceProcessDefinition = testHelper.deploy(ProcessModels.PARALLEL_GATEWAY_SUBPROCESS_PROCESS);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(ProcessModels.PARALLEL_GATEWAY_PROCESS);

    MigrationPlan migrationPlan = rule.getRuntimeService().createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("userTask1", "userTask1")
      .mapActivities("userTask2", "userTask2")
      .build();

    ProcessInstance processInstance = runtimeService
        .startProcessInstanceById(sourceProcessDefinition.getId());

    ExecutionTree executionTreeBeforeMigration =
        ExecutionTree.forExecution(processInstance.getId(), rule.getProcessEngine());

    ExecutionTree userTask1CCExecutionBefore  = executionTreeBeforeMigration
        .getLeafExecutions("userTask1")
        .get(0);
    ExecutionTree userTask2CCExecutionBefore  = executionTreeBeforeMigration
        .getLeafExecutions("userTask2")
        .get(0);
    ExecutionTree subProcessExecution  = userTask1CCExecutionBefore.getParent();

    runtimeService.setVariableLocal(subProcessExecution.getId(), "foo", "subProcessValue");
    runtimeService.setVariableLocal(userTask1CCExecutionBefore.getId(), "foo", "task1Value");
    runtimeService.setVariableLocal(userTask2CCExecutionBefore.getId(), "foo", "task2Value");

    VariableInstance task1Variable = runtimeService.createVariableInstanceQuery().variableValueEquals("foo", "task1Value").singleResult();
    VariableInstance task2Variable = runtimeService.createVariableInstanceQuery().variableValueEquals("foo", "task2Value").singleResult();

    // when
    testHelper.migrateProcessInstance(migrationPlan, processInstance);

    // then the scope variable instance has been overwritten during compaction (conform to prior behavior);
    // although this is tested here, changing this behavior may be ok in the future
    Collection<VariableInstance> variables = testHelper.snapshotAfterMigration.getVariables();
    Assert.assertEquals(2, variables.size());

    VariableInstance task1VariableAfterMigration = testHelper.snapshotAfterMigration.getVariable(task1Variable.getId());
    Assert.assertNotNull(task1VariableAfterMigration);
    Assert.assertEquals("task1Value", task1VariableAfterMigration.getValue());

    VariableInstance task2VariableAfterMigration = testHelper.snapshotAfterMigration.getVariable(task2Variable.getId());
    Assert.assertNotNull(task2VariableAfterMigration);
    Assert.assertEquals("task2Value", task2VariableAfterMigration.getValue());

  }

  @Test
  public void testVariableAtConcurrentExecutionInTransition() {
    // given
    ProcessDefinition sourceProcessDefinition = testHelper.deploy(AsyncProcessModels.ASYNC_BEFORE_USER_TASK_PROCESS);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(AsyncProcessModels.ASYNC_BEFORE_USER_TASK_PROCESS);

    MigrationPlan migrationPlan = rule.getRuntimeService()
        .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
        .mapEqualActivities()
        .build();

    ProcessInstance processInstance = rule.getRuntimeService()
        .createProcessInstanceById(sourceProcessDefinition.getId())
        .startBeforeActivity("userTask")
        .startBeforeActivity("userTask")
        .execute();

    Execution concurrentExecution = runtimeService.createExecutionQuery().activityId("userTask").list().get(0);
    Job jobForExecution = rule.getManagementService().createJobQuery().executionId(concurrentExecution.getId()).singleResult();

    runtimeService.setVariableLocal(concurrentExecution.getId(), "var", "value");

    // when
    testHelper.migrateProcessInstance(migrationPlan, processInstance);

    // then
    Job jobAfterMigration = rule.getManagementService().createJobQuery().jobId(jobForExecution.getId()).singleResult();

    testHelper.assertVariableMigratedToExecution(
        testHelper.snapshotBeforeMigration.getSingleVariable("var"),
        jobAfterMigration.getExecutionId());
  }

  @Test
  public void testVariableAtConcurrentExecutionInTransitionAddParentScope() {
    // given
    ProcessDefinition sourceProcessDefinition = testHelper.deploy(AsyncProcessModels.ASYNC_BEFORE_USER_TASK_PROCESS);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(AsyncProcessModels.ASYNC_BEFORE_SUBPROCESS_USER_TASK_PROCESS);

    MigrationPlan migrationPlan = rule.getRuntimeService()
        .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
        .mapActivities("userTask", "userTask")
        .build();

    ProcessInstance processInstance = rule.getRuntimeService()
        .createProcessInstanceById(sourceProcessDefinition.getId())
        .startBeforeActivity("userTask")
        .startBeforeActivity("userTask")
        .execute();

    Execution concurrentExecution = runtimeService.createExecutionQuery().activityId("userTask").list().get(0);
    Job jobForExecution = rule.getManagementService().createJobQuery().executionId(concurrentExecution.getId()).singleResult();

    runtimeService.setVariableLocal(concurrentExecution.getId(), "var", "value");

    // when
    testHelper.migrateProcessInstance(migrationPlan, processInstance);

    // then
    Job jobAfterMigration = rule.getManagementService().createJobQuery().jobId(jobForExecution.getId()).singleResult();

    testHelper.assertVariableMigratedToExecution(
        testHelper.snapshotBeforeMigration.getSingleVariable("var"),
        jobAfterMigration.getExecutionId());
  }

}
package com.evolveum.midpoint.model.impl.permissionset;

import com.confluxsys.idmp.job.IDMPJob;
import com.confluxsys.idmp.job.impl.IDMPJobManagerImpl;
import com.evolveum.midpoint.audit.api.AuditService;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.repo.api.RepositoryService;
import com.evolveum.midpoint.report.api.ReportManager;
import com.evolveum.midpoint.schema.result.OperationConstants;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.*;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.List;

@Component
public class IDMPReevaluateTask implements TaskHandler{

    public static final String HANDLER_URI = "http://midpoint.evolveum.com/xml/ns/public/model/idmpevaluate/handler-3";

    @Autowired(required=true)
    private TaskManager taskManager;

    @Autowired(required=true)
    private RepositoryService repositoryService;

    @Autowired(required=true)
    private AuditService auditService;

    @Autowired(required = false)
    private ReportManager reportManager;

    @Autowired(required=true)
    private PrismContext prismContext;


    @Autowired(required=true)
    private IDMPJobManagerImpl idmpJobManager;

    private static final transient Trace LOGGER = TraceManager.getTrace(IDMPReevaluateTask.class);

    @PostConstruct
    private void initialize() {
        taskManager.registerHandler(HANDLER_URI, this);
    }


    @Override
    public TaskRunResult run(Task task) {
        LOGGER.trace(IDMPReevaluateTask.class + " task starting");
        long progress = task.getProgress();
        OperationResult opResult = new OperationResult(OperationConstants.RECONCILE_ACCOUNT);
        TaskRunResult runResult = new TaskRunResult();
        runResult.setOperationResult(opResult);
        opResult.computeStatus();
        runResult.setRunResultStatus(TaskRunResult.TaskRunResultStatus.FINISHED);
        runResult.setProgress(progress);
        IDMPJob psetEvaluate = new IDMPJob(IDMPJob.JobType.EVALUATE_PERMISSION_SET, new String[] {"100"});
        IDMPJob profileRefresh = new IDMPJob(IDMPJob.JobType.REFRESH_PROFILE, new String[] {"100"});
        IDMPJob hostRefresh = new IDMPJob(IDMPJob.JobType.REFRESH_OBJECT, new String[] {"100"});
        idmpJobManager.executeJob(psetEvaluate);
        idmpJobManager.executeJob(profileRefresh);
        idmpJobManager.executeJob(hostRefresh);
        LOGGER.warn("Re-evaluation job has been submitted");
        return runResult;
    }

    @Override
    public Long heartbeat(Task task) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void refreshStatus(Task task) {
        // TODO Auto-generated method stub

    }

    @Override
    public String getCategoryName(Task task) {
        return TaskCategory.SYSTEM;
    }

    @Override
    public List<String> getCategoryNames() {
        // TODO Auto-generated method stub
        return null;
    }

}


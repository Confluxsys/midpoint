package com.evolveum.midpoint.model;

import java.util.Collection;
import java.util.List;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.Response.StatusType;
import javax.xml.namespace.QName;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.evolveum.midpoint.model.api.PolicyViolationException;
import com.evolveum.midpoint.model.controller.ModelController;
import com.evolveum.midpoint.prism.Objectable;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.delta.ItemDelta;
import com.evolveum.midpoint.prism.query.ObjectPaging;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.schema.GetOperationOptions;
import com.evolveum.midpoint.schema.SelectorOptions;
import com.evolveum.midpoint.schema.constants.MidPointConstants;
import com.evolveum.midpoint.schema.constants.ObjectTypes;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.task.api.TaskManager;
import com.evolveum.midpoint.util.exception.CommunicationException;
import com.evolveum.midpoint.util.exception.ConfigurationException;
import com.evolveum.midpoint.util.exception.ConsistencyViolationException;
import com.evolveum.midpoint.util.exception.ExpressionEvaluationException;
import com.evolveum.midpoint.util.exception.ObjectAlreadyExistsException;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SecurityViolationException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ShadowType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.UserType;

@Service
public class ModelRestService {
	
	@Autowired(required= true)
	private ModelCrudService model;
	
	@Autowired(required = true)
	private TaskManager taskManager;
	
	
	private static final Trace LOGGER = TraceManager.getTrace(ModelRestService.class);
	
	public ModelRestService(){
		
	}
	
	
	
	@GET
	@Path("/{type}/{id}")
	public <T extends ObjectType> PrismObject<T> getObject(@PathParam("type") String type, @PathParam("id") String id){
		LOGGER.info("model rest service for get operation start");
		
		Task task = taskManager.createTaskInstance();
		OperationResult parentResult = new OperationResult("get");
		try{
			
			Class<T> clazz = ObjectTypes.getClassFromRestType(type);
			
		PrismObject<T> object = model.getObject(clazz, id, null, task, parentResult);
		
		return object;
		} catch (Exception ex){
			Response.serverError().build();
		}
		
		return null;
		
	}
	
	
	
	@GET
	@Path("/example")
	public Response getOperation(){
		LOGGER.info("model rest service for example operation start");
		
		return Response.status(Status.ACCEPTED).build();
		
	}
	
	
	@POST
	@Path("/objects")
	public <T extends ObjectType> String addObject(PrismObject<T> object){
		LOGGER.info("model rest service for add operation start");
		
		Task task = taskManager.createTaskInstance();
		OperationResult parentResult = new OperationResult("add");
		try{
		String oid = model.addObject(object, task, parentResult);
		
		LOGGER.info("returned oid :  {}", oid );
		return oid;
		} catch (Exception ex){
			LOGGER.info("exception occured :  {}", ex.getMessage(), ex );
			Response.serverError().build();
		}
		
		return null;
	}
	
	@DELETE
	@Path("/{type}/{id}")
	public void deleteObject(@PathParam("type") String type, @PathParam("id") String id) throws ObjectNotFoundException, ConsistencyViolationException, CommunicationException, SchemaException, ConfigurationException, PolicyViolationException, SecurityViolationException{

		LOGGER.info("model rest service for delete operation start");
		
		Task task = taskManager.createTaskInstance();
		OperationResult parentResult = new OperationResult("delete");
		
		
		Class clazz = ObjectTypes.getClassFromRestType(type);
		
		model.deleteObject(clazz, id, task, parentResult);
		
		
	}
	
	
	@PUT
	@Path("/{type}/{oid}")
	public <T extends ObjectType> void modifyObject(@PathParam("type") String type, @PathParam("oid") String oid, 
			Collection<? extends ItemDelta> modifications) throws ObjectNotFoundException, SchemaException, ExpressionEvaluationException, CommunicationException, ConfigurationException, ObjectAlreadyExistsException, PolicyViolationException, SecurityViolationException{
		
		LOGGER.info("model rest service for modify operation start");
		
		Task task = taskManager.createTaskInstance();
		OperationResult parentResult = new OperationResult("modifyObject");
		
		
		Class clazz = ObjectTypes.getClassFromRestType(type);
		
		
		model.modifyObject(clazz, oid, modifications, task, parentResult);
		
	}

	
	@GET
	@Path("/ownerOf/{shadowOid}")
	public PrismObject<UserType> findShadowOwner(@PathParam("shadowOid") String shadowOid)
			throws ObjectNotFoundException {
		
		LOGGER.info("model rest service for find shadow owner operation start");

		Task task = taskManager.createTaskInstance();
		OperationResult parentResult = new OperationResult("find shadow owner");
		
		
		PrismObject<UserType> user = model.findShadowOwner(shadowOid, task, parentResult);
		return user;
	
	}

//	public List<PrismObject<? extends ShadowType>> listResourceObjects(String resourceOid, QName objectClass,
//			ObjectPaging paging, Task task, OperationResult parentResult) throws SchemaException,
//			ObjectNotFoundException, CommunicationException, ConfigurationException {
//	
//	}

	@POST
	@Path("/import/{objectClass}/from/{resourceOid}")
	public void importFromResource(@PathParam("resourceOid") String resourceOid, @PathParam("objectClass") String objectClass)
			throws ObjectNotFoundException, SchemaException, CommunicationException, ConfigurationException, SecurityViolationException {
	
		LOGGER.info("model rest service for import from resource operation start");

		Task task = taskManager.createTaskInstance();
		OperationResult parentResult = new OperationResult("find shadow owner");

		
		QName objClass = new QName(MidPointConstants.NS_RI, objectClass);
		
		model.importFromResource(resourceOid, objClass, task, parentResult);
		
		
		
	}

	@POST
	@Path("/test/{resourceOid}")
	public OperationResult testResource(@PathParam("resourceOid") String resourceOid) throws ObjectNotFoundException {

		LOGGER.info("model rest service for import from resource operation start");

		Task task = taskManager.createTaskInstance();

		
		model.testResource(resourceOid, task);
		
		return task.getResult();
	}
	
}
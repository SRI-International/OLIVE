package com.sri.speech.olive.api.workflow;

import com.google.protobuf.GeneratedMessageV3;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import com.sri.speech.olive.api.Olive;
import com.sri.speech.olive.api.Server;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;

public class OliveWorkflowDefinition extends  BaseWorkflow {

    private static Logger log = LoggerFactory.getLogger(OliveWorkflowDefinition.class);

//    private Olive.WorkflowDefinition workflowDefinition;

    public OliveWorkflowDefinition(String workflowDefPath) throws WorkflowException {
        try {
            workflowDefinition = Olive.WorkflowDefinition.parseFrom(new FileInputStream(Paths.get(workflowDefPath).toFile()));
        } catch (IOException e) {

            try {
                String input = Files.readString(Paths.get(workflowDefPath));
                JSONObject jo = (JSONObject)new JSONParser().parse(input);
                JSONArray orders = (JSONArray)jo.get("order");

                // Update tasks...
                for(Object jobj : orders){
                    JSONObject o = (JSONObject)jobj;
                    System.out.println("order: " + o.toString());
                    JSONArray jobDefs = (JSONArray)o.get("job_definition");
                    for (Object jdef : jobDefs){
                        JSONObject j = (JSONObject)jdef;
                        System.out.println("jobdef: " + j.toString());
                        JSONArray tasks = (JSONArray)j.get("tasks");
                        for (Object taskObj : tasks){{
                            JSONObject t = (JSONObject)taskObj;
                            JSONObject msg = (JSONObject)t.get("message_data");
                            String msgType = (String)t.get("message_type");
                            // Ugh... use some reflection to convert the task's JSON messageData to a byte string,
                            // which is what is required in the actual Workflow Definition, but was deserialized
                            // when the workflow was written to a text file so that it is readable
                            Olive.MessageType omt = Olive.MessageType.valueOf(msgType);
                            Class<?> c = Server.class_map.get(omt);
                            Method m = c.getMethod("newBuilder");
                            GeneratedMessageV3.Builder builder =  (GeneratedMessageV3.Builder)m.invoke(null);
                            JsonFormat.parser().merge(msg.toString(), builder);
                            Message finalMsg = builder.build();
                            byte[] encoded = Base64.getEncoder().encode(finalMsg.toByteString().toByteArray());
                            t.replace("message_data", msg, new String(encoded));
//                            System.out.println("task type: " + msgType);


                        }}
                    }
                }
                // Finally, convert our JSON object into a WorkflowDefinition protobuf
                Olive.WorkflowDefinition.Builder owdb = Olive.WorkflowDefinition.newBuilder();
                JsonFormat.parser().merge(jo.toString().toString(), owdb);
                workflowDefinition = owdb.build();
                return;

            } catch (IOException | ParseException | NoSuchMethodException | IllegalAccessException | InvocationTargetException ex) {
                log.debug("Error parsing workflow file: '{}' due to {}", workflowDefPath, ex);
            }

            log.warn("Failed to open Workflow Definition file: '{}'", workflowDefPath);
            throw new WorkflowException(String.format("Unable to open workflow definition '%s' because: %s ", workflowDefPath, e.getMessage()));
        }
    }

    /**
     * Actualize a Workflow for use on the specified server.
     *
     * @param server submit the encapsulated workflow definition for actualization by this server
     */
    public ActivatedWorkflow createWorkflow(Server server) throws WorkflowException {

        Olive.WorkflowActualizeRequest.Builder request = Olive.WorkflowActualizeRequest.newBuilder()
                .setWorkflowDefinition(workflowDefinition);

        Server.Result<Olive.WorkflowActualizeRequest, Olive.WorkflowActualizeResult> result = server.synchRequest(request.build());

        if (result.hasError())
        {
            // Usually a server level error ...
            throw new WorkflowException(String.format("Unable to complete the workflow request because: %s", result.getError()));
        }
        if(result.getRep().hasError()){
            // This is an error actually creating the workflow
            throw new WorkflowException(String.format("Unable to actualize workflow because: %s", result.getRep().getError()));
        }

        return new ActivatedWorkflow(result.getRep().getWorkflow(), server);

    }


}

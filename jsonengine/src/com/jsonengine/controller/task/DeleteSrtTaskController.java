package com.jsonengine.controller.task;

import java.util.List;
import java.util.logging.Logger;

import org.slim3.controller.Controller;
import org.slim3.controller.Navigation;
import org.slim3.datastore.Datastore;

import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.jsonengine.common.JEUtils;
import com.jsonengine.meta.JEDocMeta;
import com.jsonengine.service.query.QueryService;

public class DeleteSrtTaskController extends Controller {

    private static final Logger log = Logger.getLogger(DeleteSrtTaskController.class.getName());
    
    public static final String PARAM_SRTID = "srtId";
    private static final String QUENAME_JETASKS = "jetasks";

    QueryService service = new QueryService();
    @Override
    protected Navigation run() throws Exception {
        final String srtId = asString(PARAM_SRTID);
        
        final JEDocMeta jdm = JEDocMeta.get();
        List<Key> keys =
            Datastore
                .query(jdm)
                .filter(jdm.docType.equal("text"))
                .filter(jdm.indexEntries.equal("text:srtId:" + (new JEUtils()).encodePropValue(srtId)))
                .limit(500)
                .asKeyList();

        log.info("ondeletesrt: " + keys.size());
        if(keys.isEmpty()) {            
            return null;
        }
        
        Datastore.delete(keys);
        
        addDeleteSrtTask(srtId);
        
        return null;
    }

    public static void addDeleteSrtTask(String srtId) {
        final Queue que = QueueFactory.getQueue(QUENAME_JETASKS);
        final TaskOptions to =
            TaskOptions.Builder.withUrl("/task/deleteSrtTask").param(
                PARAM_SRTID,
                srtId);
        que.add(to);
    }
}

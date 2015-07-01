package com.livejournal.karino2.tobinqscript;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


import com.jsonengine.common.JEAccessDeniedException;
import com.jsonengine.common.JEConflictException;

import com.jsonengine.model.JEDoc;
import com.jsonengine.service.crud.CRUDRequest;
import com.jsonengine.service.crud.CRUDService;

import com.google.appengine.api.datastore.Transaction;

import org.apache.commons.codec.binary.Base64;
import org.slim3.datastore.Datastore;
import com.jsonengine.common.JENotFoundException;

import java.io.OutputStream;

public class ImageServlet extends HttpServlet {

    /**
     * 
     */
    
    JEDoc getJEDoc(CRUDService serv, CRUDRequest cr) {
        final Transaction tx = Datastore.beginTransaction();

	try{
            final JEDoc jeDoc = serv.getJEDoc(tx, cr);
            Datastore.commit(tx);
			return jeDoc;
        } catch (JEConflictException e) {
            throw new RuntimeException(e);
        } catch (JENotFoundException e) {
	 	return null;
        } catch (NullPointerException e){
	 	return null;
	}

   }
    
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String objId = req.getParameter("id");
	CRUDRequest jeReq = new CRUDRequest();
        CRUDService service = new CRUDService();

        CRUDRequest graphReq = new CRUDRequest();
	graphReq.setDocType("graphs");
	graphReq.setDocId(objId);

	
	JEDoc graphDoc = getJEDoc(service, graphReq);
        resp.setContentType("image/png");
	
	OutputStream out = resp.getOutputStream();
        // resp.getWriter().println("done");
	if(graphDoc != null) {
		String base64Str = (String)graphDoc.getDocValues().get("_encodedData");
		// Decode here.
		byte[] encodeBin = base64Str.getBytes();
		byte[] bImg = Base64.decodeBase64(encodeBin); 
		out.write(bImg);
	}
	
	out.close();
        
    }
}

package controllers.api;

import java.util.List;

import models.VersionedDataStore;
import models.VersionedDataStore.GlobalTx;
import models.transit.Agency;
import controllers.Api;
import controllers.Application;
import controllers.Secure;
import controllers.Security;
import play.mvc.Before;
import play.mvc.Controller;
import play.mvc.With;

@With(Secure.class)
public class AgencyController extends Controller {
	@Before
	static void initSession() throws Throwable {
		 
		if(!Security.isConnected() && !Application.checkOAuth(request, session))
			Secure.login();
	}
	
    public static void getAgency(String id) {
        try {
        	GlobalTx tx = VersionedDataStore.getGlobalTx();
        	
            if(id != null) {
	            if (!tx.agencies.containsKey(id)) {
	            	notFound();
	            	return;
	            }
	               
	            renderJSON(Api.toJson(tx.agencies.get(id), false));
            }
            else {
                renderJSON(Api.toJson(tx.agencies.values(), false));
            }
            
            tx.commit();
        } catch (Exception e) {
            e.printStackTrace();
            badRequest();
        }
    }

    public static void createAgency() {
        Agency agency;

        try {
            agency = Api.mapper.readValue(params.get("body"), Agency.class);

            // check if gtfsAgencyId is specified, if not create from DB id
            if(agency.gtfsAgencyId == null) {
                agency.gtfsAgencyId = "AGENCY_" + agency.id;
            }
            
            GlobalTx tx = VersionedDataStore.getGlobalTx();
            tx.agencies.put(agency.id, agency);
            tx.commit();

            renderJSON(Api.toJson(agency, false));
        } catch (Exception e) {
            e.printStackTrace();
            badRequest();
        }
    }


    public static void updateAgency() {
        Agency agency;

        try {
            agency = Api.mapper.readValue(params.get("body"), Agency.class);
            
            GlobalTx tx = VersionedDataStore.getGlobalTx();

            if(agency.id == null || !tx.agencies.containsKey(agency.id))
                badRequest();
            
            // check if gtfsAgencyId is specified, if not create from DB id
            if(agency.gtfsAgencyId == null)
            	agency.gtfsAgencyId = "AGENCY_" + agency.id.toString();

            tx.agencies.put(agency.id, agency);
            tx.commit();

            renderJSON(Api.toJson(agency, false));
        } catch (Exception e) {
            e.printStackTrace();
            badRequest();
        }
    }

    public static void deleteAgency(String id) {
    	GlobalTx tx = VersionedDataStore.getGlobalTx();
    	
        if(id == null) {
            badRequest();
            return;
        }
        
        if (!tx.agencies.containsKey(id)) {
        	notFound();
        	return;
        }

        tx.agencies.remove(id);
        tx.commit();
        
        ok();
    }

}

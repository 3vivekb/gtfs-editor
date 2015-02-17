package controllers.api;

import models.VersionedDataStore;
import models.VersionedDataStore.GlobalTx;
import models.transit.RouteType;
import controllers.Api;
import play.mvc.Controller;

public class RouteTypeController extends Controller {
	public static void getRouteType(String id) {
		try {
			GlobalTx tx = VersionedDataStore.getGlobalTx();
			
			if(id != null) {
				if(tx.routeTypes.containsKey(id))
					renderJSON(Api.toJson(tx.routeTypes.get(id), false));
				else
					notFound();
				
				tx.rollback();
			}
			else {
				renderJSON(Api.toJson(tx.routeTypes.values(), false));
				tx.rollback();
			}
		} catch (Exception e) {
			e.printStackTrace();
			badRequest();
		}

	}

	public static void createRouteType() {
		RouteType routeType;

		try {
			routeType = Api.mapper.readValue(params.get("body"), RouteType.class);
			
			GlobalTx tx = VersionedDataStore.getGlobalTx();
			tx.routeTypes.put(routeType.id, routeType);
			tx.commit();
			
			renderJSON(Api.toJson(routeType, false));
		} catch (Exception e) {
			e.printStackTrace();
			badRequest();
		}
	}


	public static void updateRouteType() {
		RouteType routeType;

		try {
			routeType = Api.mapper.readValue(params.get("body"), RouteType.class);

			if(routeType.id == null) {
				badRequest();
				return;
			}
			
			GlobalTx tx = VersionedDataStore.getGlobalTx();
			if (!tx.routeTypes.containsKey(routeType.id)) {
				tx.rollback();
				notFound();
				return;
			}

			tx.routeTypes.put(routeType.id, routeType);
			tx.commit();

			renderJSON(Api.toJson(routeType, false));
		} catch (Exception e) {
			e.printStackTrace();
			badRequest();
		}
	}

	// TODO: cascaded delete, etc.
	public static void deleteRouteType(String id) {
		if (id == null)
			badRequest();

		GlobalTx tx = VersionedDataStore.getGlobalTx();

		if (!tx.routeTypes.containsKey(id)) {
			tx.rollback();
			badRequest();
			return;
		}
		
		tx.routeTypes.remove(id);
		tx.commit();
		
		ok();
	}

}

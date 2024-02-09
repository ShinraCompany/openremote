package org.openremote.manager.alarm;

import org.openremote.container.message.MessageBrokerService;
import org.openremote.container.web.WebResource;
import org.openremote.manager.asset.AssetStorageService;
import org.openremote.manager.security.ManagerIdentityService;
import org.openremote.model.alarm.Alarm;
import org.openremote.model.alarm.AlarmResource;
import org.openremote.model.alarm.AlarmUserLink;
import org.openremote.model.alarm.SentAlarm;
import org.openremote.model.alarm.AlarmAssetLink;
import org.openremote.model.http.RequestParams;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response.Status;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class AlarmResourceImpl extends WebResource implements AlarmResource {
    private static final Logger LOG = Logger.getLogger(AlarmResourceImpl.class.getName());

    final protected AlarmService alarmService;
    final protected MessageBrokerService messageBrokerService;
    final protected AssetStorageService assetStorageService;

    final ManagerIdentityService managerIdentityService;

    public AlarmResourceImpl(AlarmService alarmService,
                             MessageBrokerService messageBrokerService,
                             AssetStorageService assetStorageService,
                             ManagerIdentityService managerIdentityService) {
        this.alarmService = alarmService;
        this.messageBrokerService = messageBrokerService;
        this.assetStorageService = assetStorageService;
        this.managerIdentityService = managerIdentityService;
    }

    @Override
    public SentAlarm[] getAlarms(RequestParams requestParams) {
        try{
            return alarmService.getAlarms().toArray(new SentAlarm[0]);
        } catch (IllegalArgumentException e) {
            throw new WebApplicationException("Invalid criteria set", Status.BAD_REQUEST);
        }
    }

    @Override
    public void removeAlarms(RequestParams requestParams, List<Long> ids) {
        try{
            alarmService.removeAlarms(ids);
        } catch (IllegalArgumentException e) {
            throw new WebApplicationException("Invalid criteria set", Status.BAD_REQUEST);
        }
    }

    @Override
    public  void removeAlarm(RequestParams requestParams, Long alarmId) {
        if (alarmId == null) {
            throw new WebApplicationException("Missing alarm ID", Status.BAD_REQUEST);
        }
        alarmService.removeAlarm(alarmId);
    }

    @Override
    public SentAlarm createAlarm(RequestParams requestParams, Alarm alarm) {
        SentAlarm success = alarmService.sendAlarm(alarm);
        if (success.getId() == null) {
            throw new WebApplicationException(Status.BAD_REQUEST);
        }
        return success;
    }

    @Override
    public SentAlarm createAlarmWithSource(RequestParams requestParams, Alarm alarm, Alarm.Source source, String sourceId) {
        SentAlarm success = alarmService.sendAlarm(alarm, source, sourceId);
        if (success.getId() == null) {
            throw new WebApplicationException(Status.BAD_REQUEST);
        }
        return success;
    }

    @Override
    public void updateAlarm(RequestParams requestParams, Long alarmId, SentAlarm alarm) {
        if (alarmId == null) {
            throw new WebApplicationException("Missing alarm ID", Status.BAD_REQUEST);
        }
        alarmService.updateAlarm(alarmId, alarm);
    }

    @Override
    public void setAlarmAcknowledged(RequestParams requestParams, String alarmId) {
        if (alarmId == null) {
            throw new WebApplicationException("Missing alarm ID", Status.BAD_REQUEST);
        }
        alarmService.setAlarmAcknowledged(alarmId);
    }

    @Override
    public void setAlarmStatus(RequestParams requestParams, String alarmId, String status) {
        if (alarmId == null) {
            throw new WebApplicationException("Missing alarm ID", Status.BAD_REQUEST);
        }
        alarmService.updateAlarmStatus(alarmId, status);
    }

    @Override
    public void assignUser(RequestParams requestParams, Long alarmId, String userId, String realm) {
        if (alarmId == null) {
            throw new WebApplicationException("Missing alarm ID", Status.BAD_REQUEST);
        }
        if (userId == null) {
            throw new WebApplicationException("Missing user ID", Status.BAD_REQUEST);
        }
        if (realm == null) {
            throw new WebApplicationException("Missing realm", Status.BAD_REQUEST);
        }
        alarmService.assignUser(alarmId, userId, realm);
    }

    @Override
    public List<AlarmUserLink> getUserLinks(RequestParams requestParams, Long alarmId, String realm) {
        if (alarmId == null) {
            throw new WebApplicationException("Missing alarm ID", Status.BAD_REQUEST);
        }
        if (realm == null) {
            throw new WebApplicationException("Missing realm", Status.BAD_REQUEST);
        }
        List<AlarmUserLink> result = alarmService.getUserLinks(alarmId, realm);
        return result;
    }

    @Override
    public List<SentAlarm> getAlarmsByAssetId(RequestParams requestParams, String assetId) {
        if (assetId == null) {
            throw new WebApplicationException("Missing asset ID", Status.BAD_REQUEST);
        }
        return alarmService.getAlarmsByAssetId(assetId);
    }

    @Override
    public List<SentAlarm> getOpenAlarms(RequestParams requestParams) {
        try{
            return alarmService.getOpenAlarms();
        } catch (IllegalArgumentException e) {
            throw new WebApplicationException("Invalid criteria set", Status.BAD_REQUEST);
        }
    }

    @Override
    public List<AlarmAssetLink> getAssetLinks(RequestParams requestParams, Long alarmId, String realm) {
        if (alarmId == null) {
            throw new WebApplicationException("Missing alarm ID", Status.BAD_REQUEST);
        }
        if (realm == null) {
            throw new WebApplicationException("Missing realm", Status.BAD_REQUEST);
        }
        List<AlarmAssetLink> result = alarmService.getAssetLinks(alarmId, realm);
        return result;
    }

    @Override
    public void setAssetLinks(RequestParams requestParams, ArrayList<AlarmAssetLink> links) {
        if(links.isEmpty()){
            throw new WebApplicationException("Missing links", Status.BAD_REQUEST);
        }
        alarmService.linkAssets(links);
    }
    
    @Override
    public List<AlarmAssetLink> getAssetLinks(RequestParams requestParams, Long alarmId, String realm) {
        if (alarmId == null) {
            throw new WebApplicationException("Missing alarm ID", Status.BAD_REQUEST);
        }
        if (realm == null) {
            throw new WebApplicationException("Missing realm", Status.BAD_REQUEST);
        }
        List<AlarmAssetLink> result = alarmService.getAssetLinks(alarmId, realm);
        return result;
    }
    
    @Override
    public List<AlarmAssetLink> getAssetLinks(RequestParams requestParams, Long alarmId, String realm) {
        if (alarmId == null) {
            throw new WebApplicationException("Missing alarm ID", Status.BAD_REQUEST);
        }
        if (realm == null) {
            throw new WebApplicationException("Missing realm", Status.BAD_REQUEST);
        }
        List<AlarmAssetLink> result = alarmService.getAssetLinks(alarmId, realm);
        return result;
    }
    
    protected void verifyAccess(SentAlarm sentAlarm) {
        if (sentAlarm == null) {
            LOG.fine("DENIED: Alarm not found");
            throw new WebApplicationException(Status.NOT_FOUND);
        }

        if (isSuperUser()) {
            LOG.finest("ALLOWED: Request from super user");
            return;
        }

        if (!isAuthenticated()) {
            LOG.fine("DENIED: Anonymous request are forbidden");
            throw new WebApplicationException(Status.FORBIDDEN);
        }
    }

    
}

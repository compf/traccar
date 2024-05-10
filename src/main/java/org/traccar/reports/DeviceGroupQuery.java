package org.traccar.reports;
public class DeviceGroupQuery{
    private long userId;

    public long getUserId(){
        return userId;
    }

    public void setUserId(long userId){
        this.userId=userId;
    }

    private java.util.Collection deviceIds;

    public java.util.Collection getDeviceIds(){
        return deviceIds;
    }

    public void setDeviceIds(java.util.Collection deviceIds){
        this.deviceIds=deviceIds;
    }

    private java.util.Collection groupIds;

    public java.util.Collection getGroupIds(){
        return groupIds;
    }

    public void setGroupIds(java.util.Collection groupIds){
        this.groupIds=groupIds;
    }

    private java.util.Date from;

    public java.util.Date getFrom(){
        return from;
    }

    public void setFrom(java.util.Date from){
        this.from=from;
    }

    private java.util.Date to;

    public java.util.Date getTo(){
        return to;
    }

    public void setTo(java.util.Date to){
        this.to=to;
    }

    public DeviceGroupQuery(long userId,java.util.Collection deviceIds,java.util.Collection groupIds,java.util.Date from,java.util.Date to){
        this.userId=userId;
        this.deviceIds=deviceIds;
        this.groupIds=groupIds;
        this.from=from;
        this.to=to;
    }
}


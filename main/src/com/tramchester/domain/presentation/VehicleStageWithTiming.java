//package com.tramchester.domain.presentation;
//
//import com.tramchester.domain.RawVehicleStage;
//import com.tramchester.domain.TramTime;
//import org.apache.http.annotation.Obsolete;
//
//import java.time.LocalTime;
//
//@Deprecated
//public class VehicleStageWithTiming extends RawVehicleStage implements TransportStage {
//    private TravelAction action;
//    private ServiceTime serviceTime;
//
//    public VehicleStageWithTiming(RawVehicleStage rawTravelStage, ServiceTime serviceTime, TravelAction action) {
//        super(rawTravelStage);
//        this.action = action;
//        this.serviceTime = serviceTime;
//    }
//
//    public TramTime getExpectedArrivalTime() {
//        return serviceTime.getArrivalTime();
//    }
//
//    public TramTime getFirstDepartureTime() {
//       return serviceTime.getDepartureTime();
//    }
//
//    public int getDuration() {
//        return cost;
//    }
//
//    @Override
//    public String toString() {
//        return "VehicleStageWithTiming{" +
//                "rawTravelStage=" + super.toString() +
//                ", serviceTime=" + serviceTime +
//                '}';
//    }
//
//    @Override
//    public String getHeadSign() {
//        return serviceTime.getHeadSign();
//    }
//
//    @Override
//    public TravelAction getAction() {
//        return action;
//    }
//
//
//}

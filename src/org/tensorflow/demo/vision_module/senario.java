package org.tensorflow.demo.vision_module;

import java.util.ArrayList;
import java.util.Arrays;

public class senario {

    public static final String startStationString = "출발역을 말씀해주세요?";
//    public static final String startExitString = "몇번 출구에서 출발 하시나요?";
    public static final String destStationString = "도착역을 말씀해주세요";
//    public static final String destExitString= "몇번 출구로 나가시나요?";
    public static final String startavi ="길찾기 네비게이션 시작합니다";


    static final ArrayList<String> senarioArray = new ArrayList<String>(Arrays.asList(senario.startStationString,senario.destStationString));

    static final public String getI(int i){
        return senarioArray.get(i);
    }


}

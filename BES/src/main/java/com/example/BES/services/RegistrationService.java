package com.example.BES.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.springframework.stereotype.Component;

@Component
public class RegistrationService {
    Random random;
    List<Integer> battlers; // = new ArrayList<>(Arrays.asList(1,2,3,4,5,6,7,8));
    int totalParticipants = 50;
    public RegistrationService(){
        battlers = new ArrayList<>();
        int n = totalParticipants;
        for(int i = 0; i<n;i++){
            battlers.add(i+1);
        }
    }
    
    public int drawRandomNumberService(){
        // need to handle empty list
        if(battlers.size() <= 0){
            return -1;
        }
        random = new Random();
        int index = random.nextInt(battlers.size());
        int number = battlers.get(index);
        battlers.remove(index);
        return number;
        // information needed:
        // Total num of battlers : List
        // Early bird or Walk In 
        
        // Pick a num from a list
        // remove the num from the list 
        // return the num
    }

    public boolean validateRegistrationService(){
        return true;
    }
    
    // This is for walk ins battlers then we need to account for its
    public void increaseBattlersList(){
        battlers.add(totalParticipants + 1);
        totalParticipants += 1;
    }
}

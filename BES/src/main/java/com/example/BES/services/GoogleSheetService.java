package com.example.BES.services;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.BES.config.GoogleSheetConfig;
import com.example.BES.dtos.GoogleSheetFileDto;
import com.google.api.services.sheets.v4.model.BatchGetValuesResponse;
import com.google.api.services.sheets.v4.model.ValueRange;

@Service
public class GoogleSheetService {
    // if any new category just add here and update the constructor to map the function
    // need to update dto as well
    private final static String KEYWORD = "Categories";
    private final static List<String> PAYMENT_KEYWORDS = new ArrayList<>(Arrays.asList("DonePayment", "email", "name","categories", "local/overseas"));

    private final static String POPPING = "Popping";
    private final static String WAACKING = "Waacking";
    private final static String LOCKING = "Locking";
    private final static String BREAKING = "Breaking";
    private final static String HIPHOP = "Hiphop";
    private final static String OPEN = "Open";
    private final static String AUDIENCE = "Audience";
    
    @Autowired
    GoogleSheetConfig config;

    Map<String, BiConsumer<GoogleSheetFileDto, List<String>>> actions;
    List<String> genres;

    public GoogleSheetService(){
        genres = new ArrayList<>(Arrays.asList(POPPING, WAACKING, LOCKING, BREAKING, HIPHOP, OPEN));
        actions = new HashMap<>();
        actions.put(POPPING, (dto,list) -> dto.setPopping(categoriesCount(list, POPPING)));
        actions.put(WAACKING, (dto,list) -> dto.setWaacking(categoriesCount(list, WAACKING)));
        actions.put(LOCKING, (dto,list) -> dto.setLocking(categoriesCount(list, LOCKING)));
        actions.put(BREAKING, (dto,list) -> dto.setBreaking(categoriesCount(list, BREAKING)));
        actions.put(HIPHOP, (dto,list) -> dto.setHiphop(categoriesCount(list, HIPHOP)));
        actions.put(OPEN, (dto,list) -> dto.setOpen(categoriesCount(list, OPEN)));
        actions.put(AUDIENCE, (dto,list) -> dto.setAudience(categoriesCount(list, AUDIENCE)));
    }
    
    // This is hardcoded we cannot confirmed where it will be located
    /* Correct order should be: 
     * 1. Search first row to identify the index of rows that has certain keyword eg. Category
     * 2. Store the indices in list, and by going through the list transform index to alphabet and in H:H format
     * 3. 
    */
    
    public GoogleSheetFileDto getSheetInformationById(String fileId) throws IOException{
        GoogleSheetFileDto dto = new GoogleSheetFileDto();
        // Get the header
        List<String> headers  = readSheetHeader(fileId);

        // Identify the Alphabet of columns that have "Categories"
        List<String> matchingColumnIndices = new ArrayList<>();
        for(int i = 0; i < headers.size(); i++){
            if(headers.get(i).equalsIgnoreCase(KEYWORD)){
                String colLetter = colIndexToLetter(i + 1);
                matchingColumnIndices.add(colLetter + ":" + colLetter);
            }
        }                          
        
        // Get the value based on the columns identified
        // usually there will be only two which are local and overseas 
        BatchGetValuesResponse response = config
                                .getSheets()
                                .spreadsheets()
                                .values()
                                .batchGet(fileId)
                                .setRanges(matchingColumnIndices)
                                .execute();

        List<ValueRange> valueRanges = response.getValueRanges();

        List<List<Object>> localGenre = valueRanges.get(0).getValues();
        List<List<Object>> overseasGenre = valueRanges.get(1).getValues();
        List<String> combined = new ArrayList<>();
        for(int i = 1; i < localGenre.size(); i++){
            if(localGenre.get(i).size() == 0){
                combined.add(overseasGenre.get(i).get(0).toString());
            }else{
                combined.add(localGenre.get(i).get(0).toString());
            }
        }
        setDtoCategory(dto, combined);
        return dto;
    }

    public List<String> updatePaymentStatus(String fileId) throws IOException{
        // Read thru the google sheets
        // information needed email, categories, donePayment, local/overseas
        // if no overseas should assume only local
        // if no payment by default should send email
        // need database to store the information eg. payment status, registered, audition number and etc
        // if donePayment column not there create one with all false
        List<String> headers = readSheetHeader(fileId);
        List<String> matchingColumnIndices = new ArrayList<>();
        for(int i = 0; i< headers.size(); i ++){
           for(String keyword: PAYMENT_KEYWORDS){
            if(headers.get(i).toLowerCase().contains(keyword.toLowerCase())){
                String colLetter = colIndexToLetter(i + 1);
                matchingColumnIndices.add(colLetter + ":" + colLetter);
            }
           }
        }
        System.err.println(matchingColumnIndices);
        return matchingColumnIndices;
    }

    private Integer categoriesCount(List<String> data, String Category){
        return (int)data.stream()
                .filter(s -> s.toLowerCase().contains(Category.toLowerCase()))
                .count();
    }

    private void setDtoCategory(GoogleSheetFileDto dto, List<String> data){
        Set<String> categories = new HashSet<String>(data);
        for (String category: categories){
            List<String> normalizeCategories = normalizeGenre(category, genres);
            for(String normalizeCategory: normalizeCategories){
                System.out.println(normalizeCategory);
                actions.get(normalizeCategory).accept(dto, data);
            }
        }
    }

    private List<String> normalizeGenre(String event, List<String> genres){
        List<String> matchingGenre = new ArrayList<>();
        for(String genre : genres){
            if(event.contains(genre)){
                matchingGenre.add(genre);
            }
        }
        System.out.println(matchingGenre);
        return matchingGenre;
    }

    private static String colIndexToLetter(int index) {
        StringBuilder result = new StringBuilder();
        while (index > 0) {
            int rem = (index - 1) % 26;
            result.insert(0, (char) ('A' + rem));
            index = (index - 1) / 26;
        }
        return result.toString();
    }

    private List<String> readSheetHeader(String fileId) throws IOException{
        ValueRange firstRow = config
                .getSheets()
                .spreadsheets()
                .values()
                .get(fileId, "1:1")
                .execute();
        List<String> headers = firstRow.getValues().get(0)
                .stream()
                .map(object -> Objects.toString(object, null))
                .toList();
        return headers;
    }
}
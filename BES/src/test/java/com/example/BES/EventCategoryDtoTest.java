package com.example.BES;

import com.example.BES.dtos.GetEventCategoryDto;
import com.example.BES.models.Event;
import com.example.BES.models.EventCategory;
import com.example.BES.respositories.EventCategoryRepo;
import com.example.BES.respositories.EventRepo;
import com.example.BES.services.EventCategoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EventCategoryDtoTest {

    @Autowired EventCategoryService eventCategoryService;
    @Autowired EventRepo eventRepo;
    @Autowired EventCategoryRepo eventCategoryRepo;

    @Test
    void judgeAssignmentMode_is_exposed_in_dto() {
        Event event = new Event();
        event.setEventName("DtoTestEvent");
        eventRepo.save(event);

        EventCategory cat = new EventCategory();
        cat.setEvent(event);
        cat.setName("Open");
        cat.setJudgeAssignmentMode(true);
        eventCategoryRepo.save(cat);

        List<GetEventCategoryDto> dtos = eventCategoryService.getCategoriesByEventService("DtoTestEvent");
        assertThat(dtos).hasSize(1);
        assertThat(dtos.get(0).judgeAssignmentMode).isTrue();
    }
}

package com.example.BES.services;

import java.io.IOException;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import com.example.BES.enums.Constant;
import com.example.BES.enums.EmailTemplates;
import com.example.BES.models.EventGenreParticipantId;
import com.example.BES.models.Participant;
import com.example.BES.respositories.GenreRepo;
import com.google.zxing.WriterException;

import jakarta.activation.DataHandler;
import jakarta.activation.DataSource;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.util.ByteArrayDataSource;

@Service
public class MailSenderService {
    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    EmailTemplates emailTemplates;

    @Autowired
    QrCodeService qrService;

    @Autowired
    GenreRepo genreRepo;

    public void sendEmailWithAttachment(String eventName, Participant receiver, List<EventGenreParticipantId> ids) throws MessagingException, WriterException, IOException{
        EmailTemplates.Template template = emailTemplates.getEvents().get(normalizeKey(eventName)); 
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper messageHelper = new MimeMessageHelper(mimeMessage, true);
        for(EventGenreParticipantId id : ids){
            // insert real domain here
            String registerLink = String.format("http://blim.local/api/v1/event/register-participant/%d/%d/%d",id.getParticipantId(),id.getEventId(), id.getGenreId());
            byte[] sourceBytes = qrService.generateQrCode(registerLink, 150, 150);
            DataSource dataSource = new ByteArrayDataSource(sourceBytes, "image/jpeg");
            MimeBodyPart attachmentPart = new MimeBodyPart();
            attachmentPart.setDataHandler(new DataHandler(dataSource));
            String genreName = genreRepo.findById(id.getGenreId()).orElse(null).getGenreName();
            attachmentPart.setFileName(String.format("%s.jpg", genreName));
            messageHelper.addAttachment(String.format("%s.jpg", genreName), dataSource);;
        }
            // use with MimeBodyPart
        messageHelper.setFrom(Constant.SENDER_EMAIL.getLabel());
        messageHelper.setTo(receiver.getParticipantEmail());
        messageHelper.setText(template.getBody().replace("{name}", receiver.getParticipantName()));
        messageHelper.setSubject(template.getSubject());
        mailSender.send(mimeMessage);
    }

    public static String normalizeKey(String key) {
        if (key == null) {
            return null;
        }
        // Replace space and dot with underscore
        return key.replaceAll("[ .]", "_");
    }
}

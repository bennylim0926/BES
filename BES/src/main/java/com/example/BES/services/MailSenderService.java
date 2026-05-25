package com.example.BES.services;

import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import com.example.BES.dtos.GetEmailTemplateDto;
import com.example.BES.enums.Constant;
import com.example.BES.models.EventGenreParticipant;
import com.example.BES.models.EventParticipant;
import com.example.BES.models.EventParticipantTeamMember;
import com.example.BES.models.Participant;
import com.google.zxing.WriterException;

import jakarta.activation.DataSource;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.util.ByteArrayDataSource;
import org.springframework.core.env.Environment;

@Service
public class MailSenderService {
    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    EmailTemplateService emailTemplateService;

    @Autowired
    QrCodeService qrService;

    @Autowired
    private Environment env;

    public void sendEmailWithAttachment(String eventName, Participant receiver,
            EventParticipant ep, List<EventGenreParticipant> egps, String referenceCode)
            throws MessagingException, WriterException, IOException {
        GetEmailTemplateDto template = emailTemplateService.getTemplateByEventName(eventName);
        if (template == null) {
            throw new RuntimeException("No email template found for event: " + eventName);
        }
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper messageHelper = new MimeMessageHelper(mimeMessage, true);

        // Use first EGP id for QR code link
        EventGenreParticipant first = egps.get(0);
        String registerLink = String.format(
            "%s/api/v1/event/register-participant/%d/%d",
            env.getProperty("DOMAIN"),
            first.getId().getParticipantId(),
            first.getId().getEventId());
        byte[] sourceBytes = qrService.generateQrCode(registerLink, 350, 350);
        DataSource dataSource = new ByteArrayDataSource(sourceBytes, "image/jpeg");
        messageHelper.addAttachment("audition-qr.jpg", dataSource);

        messageHelper.setFrom(Constant.SENDER_EMAIL.getLabel());
        messageHelper.setTo(receiver.getParticipantEmail());

        String displayName = ep.getDisplayName() != null ? ep.getDisplayName() : receiver.getParticipantName();

        boolean hasTeam = egps.stream().anyMatch(egp -> isTeamFormat(egp.getFormat()));
        boolean hasSolo = egps.stream().anyMatch(egp -> !isTeamFormat(egp.getFormat()));

        String body = template.getBody();
        // Process conditional blocks before variable substitution
        body = processConditionalBlock(body, "team", hasTeam);
        body = processConditionalBlock(body, "solo", hasSolo);
        // Variable substitution
        body = body
            .replace("{name}",            displayName)
            .replace("{stageName}",       ep.getStageName()  != null ? ep.getStageName()  : "")
            .replace("{teamName}",        ep.getTeamName()   != null ? ep.getTeamName()   : "")
            .replace("{members}",         buildMemberString(ep.getTeamMembers()))
            .replace("{soloCategories}",  buildCategoryString(egps, false))
            .replace("{teamCategories}",  buildCategoryString(egps, true))
            .replace("{refCode}",         referenceCode != null ? referenceCode : "");

        messageHelper.setText(body);
        messageHelper.setSubject(template.getSubject());

        mailSender.send(mimeMessage);
    }

    private String buildMemberString(List<EventParticipantTeamMember> members) {
        if (members == null || members.isEmpty()) return "";
        return members.stream()
            .map(EventParticipantTeamMember::getMemberName)
            .collect(Collectors.joining(", "));
    }

    private String buildCategoryString(List<EventGenreParticipant> egps, boolean teamOnly) {
        return egps.stream()
            .filter(egp -> teamOnly ? isTeamFormat(egp.getFormat()) : !isTeamFormat(egp.getFormat()))
            .map(egp -> egp.getGenre().getGenreName())
            .collect(Collectors.joining(", "));
    }

    private boolean isTeamFormat(String format) {
        return format != null && !format.equalsIgnoreCase("1v1");
    }

    /**
     * If condition is true: strip the open/close tags and keep the content.
     * If condition is false: remove the entire block including its content.
     */
    private String processConditionalBlock(String body, String tag, boolean condition) {
        String openTag  = "\\{if:" + tag + "\\}";
        String closeTag = "\\{endif:" + tag + "\\}";
        if (condition) {
            return body.replaceAll(openTag, "").replaceAll(closeTag, "");
        } else {
            return Pattern.compile(openTag + ".*?" + closeTag, Pattern.DOTALL)
                          .matcher(body).replaceAll("");
        }
    }

    public static String normalizeKey(String key) {
        if (key == null) {
            return null;
        }
        return key.replaceAll("[ .]", "_");
    }
}

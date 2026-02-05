package com.old.silence.job.server.api.assembler;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.core.convert.converter.Converter;
import com.old.silence.core.mapstruct.MapStructSpringConfig;
import com.old.silence.job.server.domain.model.NotifyRecipient;
import com.old.silence.job.server.dto.NotifyRecipientCommand;
import com.old.silence.job.server.vo.CommonLabelValueResponseVO;
import com.old.silence.job.server.vo.NotifyRecipientResponseVO;


@Mapper(componentModel = "spring", uses = MapStructSpringConfig.class)
public interface NotifyRecipientMapper extends Converter<NotifyRecipientCommand, NotifyRecipient> {


    @Override
    NotifyRecipient convert(NotifyRecipientCommand source);


    NotifyRecipientResponseVO convert(NotifyRecipient notifyRecipient);

}

package com.bacpham.kanban_service.dto.request;


import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SupplierFormDTO {
      String title;
      String layout;
      int labelCol;
      int wrapperCol;
      List<FormItem> formItems;
}

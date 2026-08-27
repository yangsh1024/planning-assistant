package com.ysh.planning.common.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageData<T> {

    private long total;
    private int page;
    private int pageSize;
    private List<T> list;
}

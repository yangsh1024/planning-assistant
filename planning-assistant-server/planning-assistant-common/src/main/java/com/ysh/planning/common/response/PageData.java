package com.ysh.planning.common.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
/** 分页查询的统一数据结构，固定暴露总数、页码、页大小和列表。 */
public class PageData<T> {

    private long total;
    private int page;
    private int pageSize;
    private List<T> list;
}

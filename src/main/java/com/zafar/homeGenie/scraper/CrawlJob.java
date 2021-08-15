package com.zafar.homeGenie.scraper;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.util.List;

public class CrawlJob {
    private String name;
    private List<CrawlTask> taskList;

    public CrawlJob(String name, List<CrawlTask> pageList) {
        this.name = name;
        this.taskList = pageList;
    }

    public String getName() {
        return name;
    }

    public CrawlJob setName(String name) {
        this.name = name;
        return this;
    }

    public List<CrawlTask> getTaskList() {
        return taskList;
    }

    public CrawlJob setTaskList(List<CrawlTask> taskList) {
        this.taskList = taskList;
        return this;
    }

    public static class CrawlTask {

        private String address;
        private String name;

        public String getAddress() {
            return address;
        }

        public CrawlTask setAddress(String address) {
            this.address = address;
            return this;
        }

        public String getName() {
            return name;
        }

        public CrawlTask setName(String name) {
            this.name = name;
            return this;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;

            if (o == null || getClass() != o.getClass()) return false;

            CrawlTask webPage = (CrawlTask) o;

            return new EqualsBuilder().append(name, webPage.name).isEquals();
        }

        @Override
        public int hashCode() {
            return new HashCodeBuilder(17, 37).append(name).toHashCode();
        }
    }
}

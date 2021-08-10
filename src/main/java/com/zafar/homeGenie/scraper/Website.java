package com.zafar.homeGenie.scraper;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.util.List;

public class Website {
    private String name;
    private List<WebPage> pageList;

    public Website(String name, List<WebPage> pageList) {
        this.name = name;
        this.pageList = pageList;
    }

    public String getName() {
        return name;
    }

    public Website setName(String name) {
        this.name = name;
        return this;
    }

    public List<WebPage> getPageList() {
        return pageList;
    }

    public Website setPageList(List<WebPage> pageList) {
        this.pageList = pageList;
        return this;
    }

    public static class WebPage {

        private String address;
        private String name;

        public String getAddress() {
            return address;
        }

        public WebPage setAddress(String address) {
            this.address = address;
            return this;
        }

        public String getName() {
            return name;
        }

        public WebPage setName(String name) {
            this.name = name;
            return this;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;

            if (o == null || getClass() != o.getClass()) return false;

            WebPage webPage = (WebPage) o;

            return new EqualsBuilder().append(name, webPage.name).isEquals();
        }

        @Override
        public int hashCode() {
            return new HashCodeBuilder(17, 37).append(name).toHashCode();
        }
    }
}

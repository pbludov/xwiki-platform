<!--
  * See the NOTICE file distributed with this work for additional
  * information regarding copyright ownership.
  *
  * This is free software; you can redistribute it and/or modify it
  * under the terms of the GNU Lesser General Public License as
  * published by the Free Software Foundation; either version 2.1 of
  * the License, or (at your option) any later version.
  *
  * This software is distributed in the hope that it will be useful,
  * but WITHOUT ANY WARRANTY; without even the implied warranty of
  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
  * Lesser General Public License for more details.
  *
  * You should have received a copy of the GNU Lesser General Public
  * License along with this software; if not, write to the Free
  * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
  * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 -->


<!--
  The LivedataPagination component is used to change the current page
  displayed by the layout.
  What it actually does is that it fetches new data corresponding
  to the chosen page number,
  which then updates the entries array in `config.data.data.entries`,
  which has the effect of changing the displayed entries in the layout
-->
<template>
  <!-- Pagination -->
  <nav class="livedata-pagination">

    <!--
      Display the pagination current entry range
      Can be shown / hiden by the `pagination.showEntryRange` property
      in the Livedata meta config
    -->
    <span
      class="pagination-current-entries"
      v-if="data.meta.pagination.showEntryRange"
    >
      {{ $t('livedata.pagination.currentEntries', [
        logic.getFirstIndexOfPage() + 1,
        logic.getLastIndexOfPage() + 1,
        data.data.count,
      ])}}
    </span>

    <!--
      Select the pagination size (number of entries per page)
      Can be shown / hidden by the `pagination.showPageSizeDropdown` property
      in the Livedata meta config
    -->
    <span
      class="pagination-page-size"
      v-if="data.meta.pagination.showPageSizeDropdown"
    >
      {{ $t('livedata.pagination.pageSize') }}
      <select
        @change="logic.setPageSize(+$event.target.value)"
      >
        <!-- Page sizes (get from the `pagination.pageSizes` config -->
        <option
          v-for="pageSize in pageSizes"
          :key="pageSize"
          :value="pageSize"
          :selected="pageSize === data.query.limit"
        >{{ pageSize }}</option>
      </select>
    </span>

    <!--
      The actual pagination widget
      It dislays the the available pages numbers, and change to them on click.
      Not all page numbers are show depending of the `pagination.maxShownPages`
      property in the Livedata meta config.
      Arrows can be shown to go to first, last, previous, next page.
    -->
    <nav class="pagination-indexes">
      {{ $t('livedata.pagination.page') }}
      <!--
        Go to First Page button
        Can be shown / hiden by the `pagination.showFirstLast` property
        in the Livedata meta config
      -->
      <a
        :class="['page-nav', {
          'disabled': isFirstPage,
        }]"
        v-if="data.meta.pagination.showFirstLast"
        :title="$t('livedata.pagination.first')"
        href="#"
        @click.prevent="!isFirstPage && logic.setPageIndex(0)"
      >
        <span class="fa fa-angle-double-left"></span>
      </a>

      <!--
        Go to Previous Page button
        Can be shown / hiden by the `pagination.showNextPrevious` property
        in the Livedata meta config
      -->
      <a
        :class="['page-nav', {
          'disabled': isFirstPage,
        }]"
        v-if="data.meta.pagination.showNextPrevious"
        :title="$t('livedata.pagination.previous')"
        href="#"
        @click.prevent="!isFirstPage && logic.setPageIndex(logic.getPageIndex() - 1)"
      >
        <span class="fa fa-angle-left"></span>
      </a>


      <!--
        Page Numbers
        Shown page numbers are specified by `this.paginationIndexesAndDots`
        computed property, which take into account the `pagination.maxShownPages`
        property in the Livedata meta config.
        It displays "..." when pages numbers are not displayed.
      -->
      <template
        v-for="(pageIndex, i) in paginationIndexesAndDots"
      >
        <!--
          Ellispis (for hidden pages)
          v-for keys need to be unique, so we use "..." + the index of the
          ellipsis in the paginationIndexesAndDots array to ensure this
        -->
        <span
          v-if="pageIndex === '...'"
          :key="'...' + i"
        >...</span>
        <!-- Page numbers -->
        <a
          v-else
          :key="pageIndex"
          :class="{
            'page-nav': true,
            'current': pageIndex === logic.getPageIndex(),
          }"
          href="#"
          @click.prevent="logic.setPageIndex(pageIndex)"
        >{{ pageIndex + 1 }}</a>
        <!-- pageIndex + 1 because pageIndex are 0-based -->
      </template>

      <!--
        Go to Next Page button
        Can be shown / hiden by the `pagination.showNextPrevious` property
        in the Livedata meta config
      -->
      <a
        :class="['page-nav', {
          'disabled': isLastPage,
        }]"
        v-if="data.meta.pagination.showNextPrevious"
        :title="$t('livedata.pagination.next')"
        href="#"
        @click.prevent="!isLastPage && logic.setPageIndex(logic.getPageIndex() + 1)"
      >
        <span class="fa fa-angle-right"></span>
      </a>

      <!--
        Go to Last Page button
        Can be shown / hiden by the `pagination.showFirstLast` property
        in the Livedata meta config
      -->
      <a
        :class="['page-nav', {
          'disabled': isLastPage,
        }]"
        v-if="data.meta.pagination.showFirstLast"
        :title="$t('livedata.pagination.last')"
        href="#"
        @click.prevent="!isLastPage && logic.setPageIndex(logic.getPageCount() - 1)"
      >
        <span class="fa fa-angle-double-right"></span>
      </a>

    </nav>

  </nav>
</template>


<script>
export default {

  name: "LivedataPagination",

  inject: ["logic"],

  computed: {
    data () { return this.logic.data; },

    // Whether current page is first page
    isFirstPage () {
      return this.logic.getPageIndex() === 0;
    },

    // Whether current page is last page
    isLastPage () {
      return this.logic.getPageIndex() === this.logic.getPageCount() - 1;
    },

    // Return the page indexes (= page number but 0-based) to be displayed
    // It follows the following algorithm:
    // - always display the current page index
    // - then adds the following in order, until matching the shown page count
    //   - first page index
    //   - last page index
    //   - page indexes around current page index, alternating before and after it
    paginationIndexes () {
      // Total count of pages
      const pageCount = this.logic.getPageCount();
      // Number of pages to show on widget
      const maxShownPages = this.data.meta.pagination.maxShownPages;
      // Current page index
      const currentPageIndex = this.logic.getPageIndex();
      // Page indexes to be displayed
      const pageIndexes = [];
      // Function to add a page inside the pageIndexes array
      // it verifies if the page index is valid and not already pushed
      const addPage = pageIndex => {
        if (pageIndex >= 0 && pageIndex < pageCount && !pageIndexes.includes(pageIndex)) {
          pageIndexes.push(pageIndex);
        }
      };

      // Pages to display at the very least
      if (maxShownPages >= 1) { addPage(currentPageIndex); }
      if (maxShownPages >= 2) { addPage(0); }
      if (maxShownPages >= 3) { addPage(pageCount - 1); }

      // Add pages index around current pages
      // Until we have the specified count of pages to display
      let i = 1;
      const bound = Math.max(currentPageIndex, pageCount - currentPageIndex);
      while (pageIndexes.length < maxShownPages && Math.abs(i) < bound) {
        addPage(currentPageIndex + i);
        if (i > 0) {
          i *= -1;
        } else {
          i = (i * -1) + 1;
        }
      }

      // Return the sorted array
      return pageIndexes.sort((a, b) => a - b);
    },

    // Add a "..." string item between page indexes that are not
    // following one each other
    // So that we know where to display the ellipsis in widget
    paginationIndexesAndDots () {
      const indexesAndDots = [];
      this.paginationIndexes.forEach((index, i, indexes) => {
        indexesAndDots.push(index);
        if (indexes[i + 1] && indexes[i + 1] !== index + 1) {
          indexesAndDots.push("...");
        }
      });
      return indexesAndDots;
    },

    /**
     * Merges and sort the pages sizes from the pagination as well as the configured limit.
     * @returns {number[]} the list of page sizes
     */
    pageSizes() {
      const pageSizesSet = new Set();
      this.data.meta.pagination.pageSizes.forEach(it => pageSizesSet.add(it));
      const limit = this.data.query.limit;
      if (limit) {
        pageSizesSet.add(limit);
      }
      // Converts the set of page size values into an array and sorts them in ascending numerical order.
      return Array.from(pageSizesSet).sort((a, b) => a - b);
    }
  },

};
</script>


<style>

.livedata-pagination {
  color: #777777;
  margin-left: 1rem;
  font-size: 0.9em;
}

.livedata-pagination .pagination-indexes {
  display: inline-block;
  margin-left: 2rem;
  user-select: none;
  -webkit-user-select: none;
  -ms-user-select: none;
}

.livedata-pagination .page-nav {
  padding: 0px 3px;
  color: inherit;
  cursor: pointer;
}
.livedata-pagination .page-nav.current {
  font-weight: bold;
}
.livedata-pagination .page-nav:hover {
  text-decoration: underline;
}
.livedata-pagination .page-nav:not(:hover) {
  text-decoration: none;
}

.livedata-pagination .page-nav.disabled {
  color: gray;
  cursor: default;
  text-decoration: none;
}

.livedata-pagination .pagination-page-size select {
  height: unset;
  padding: 2px 4px;
}

/* Responsive mode */
@media screen and (max-width: @screen-xs-max) {
  /* Stack the pagination elements vertically. */
  .livedata-pagination {
    display: flex;
    flex-direction: column;
    text-align: right;
  }

  /* Hide the page size element. */
  .livedata-pagination .pagination-page-size {
    display: none;
  }

  .livedata-pagination .pagination-indexes {
    margin-left: 0;
  }
}

</style>

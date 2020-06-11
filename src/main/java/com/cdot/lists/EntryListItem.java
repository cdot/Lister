/**
 * @copyright C-Dot Consultants 2020 - MIT license
 */
package com.cdot.lists;

/**
 * Interface to items in an EntryList
 */
interface EntryListItem {
    EntryList getContainer();

    /**
     * Set the item's text
     * @param str new text
     */
    void setText(String str);

    /**
     * Get the item's text
     * @return a text string representing the item in the list
     */
    String getText();
}

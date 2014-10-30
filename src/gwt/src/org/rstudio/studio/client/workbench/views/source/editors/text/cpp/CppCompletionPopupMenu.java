package org.rstudio.studio.client.workbench.views.source.editors.text.cpp;

import org.rstudio.core.client.CommandWithArg;
import org.rstudio.core.client.Rectangle;
import org.rstudio.core.client.theme.res.ThemeStyles;
import org.rstudio.core.client.widget.ScrollableToolbarPopupMenu;
import org.rstudio.studio.client.workbench.views.console.shell.editor.InputEditorPosition;
import org.rstudio.studio.client.workbench.views.source.editors.text.DocDisplay;
import org.rstudio.studio.client.workbench.views.source.editors.text.ace.Position;
import org.rstudio.studio.client.workbench.views.source.model.CppCompletion;

import com.google.gwt.core.client.JsArray;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.event.logical.shared.CloseEvent;
import com.google.gwt.event.logical.shared.CloseHandler;
import com.google.gwt.event.logical.shared.SelectionEvent;
import com.google.gwt.event.logical.shared.SelectionHandler;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.MenuItem;
import com.google.gwt.user.client.ui.PopupPanel;

public class CppCompletionPopupMenu extends ScrollableToolbarPopupMenu
{
   CppCompletionPopupMenu(DocDisplay docDisplay, Position completionPosition)
   {  
      docDisplay_ = docDisplay;
      completionPosition_ = completionPosition;
      
      addToolTipHandler();
      
      addStyleName(ThemeStyles.INSTANCE.statusBarMenu());
   }
   
   public void setText(String text)
   {
      JsArray<CppCompletion> completions = JsArray.createArray().cast();
      completions.push(CppCompletion.create(text));
      setCompletions(completions, null);
   }
     
   public void setCompletions(JsArray<CppCompletion> completions, 
                              CommandWithArg<CppCompletion> onSelected)
   {
      // save completions and selectable state
      completions_ = completions;
      onSelected_ = onSelected;
      
      // clear existing items
      updatingMenu_ = true;
      menuBar_.clearItems();
      
      // add items (remember first item for programmatic selection)
      MenuItem firstItem = null;
      for (int i = 0; i<completions.length(); i++)
      {
         final CppCompletion completion = completions.get(i);
         MenuItem menuItem = new MenuItem(completion.getTypedText(), 
               new ScheduledCommand() {
            @Override
            public void execute()
            {
               docDisplay_.setFocus(true); 
               if (isSelectable())
                  onSelected_.execute(completion);
            }
         });
         
         addItem(menuItem);
         
         if (i == 0)
            firstItem = menuItem;
      }
      updatingMenu_ = false;
      
      
      // select first item
      if (isSelectable() && (firstItem != null))
         selectItem(firstItem);
      
      if (completions.length() > 0)
      {
         showMenu();
      }
      else
      {
         setVisible(false);
         if (toolTip_ != null)
            toolTip_.setVisible(false);
      }
   }
   
   public void acceptSelected()
   {
      int index = getSelectedIndex();
      if (index != -1)
      {
         if (isSelectable())
            onSelected_.execute(completions_.get(index));
      }
      hide();
   }
   
   private void showMenu()
   {
      setPopupPositionAndShow(new PositionCallback()
      {
         public void setPosition(int offsetWidth, int offsetHeight)
         {
            InputEditorPosition position = 
               docDisplay_.createInputEditorPosition(completionPosition_);  
            Rectangle bounds = docDisplay_.getPositionBounds(position);
            
            int windowBottom = Window.getScrollTop() + 
                               Window.getClientHeight() ;
            int cursorBottom = bounds.getBottom() ;
            
            // figure out whether we should show below (do this 
            // only once so that we maintain the menu orientation
            // while filtering)
            if (showBelow_ == null)
               showBelow_ = windowBottom - cursorBottom >= offsetHeight;
            
            final int PAD = 3;
            if (showBelow_)
               setPopupPosition(bounds.getLeft(), cursorBottom + PAD) ;
            else
               setPopupPosition(bounds.getLeft(), 
                                bounds.getTop() - offsetHeight) ;
         }
      });
   }
   
   private boolean isSelectable()
   {
      return onSelected_ != null;
   }
   
   private void addToolTipHandler()
   {
      toolTip_ = new CppCompletionToolTip();
      
      addSelectionHandler(new SelectionHandler<MenuItem>()
      {
         public void onSelection(SelectionEvent<MenuItem> event)
         { 
            // bail if we are updating the menu
            if (updatingMenu_)
               return;
            
            // bail if we have no more tooltip
            if (toolTip_ == null)
               return;
            
            // screen unselectable
            if (!isSelectable())
            {
               toolTip_.setVisible(false);
               return;
            }
               
            // screen unable to find menu
            final MenuItem selectedItem = event.getSelectedItem();
            int index = menuBar_.getItemIndex(selectedItem);
            if (index == -1)
            {
               toolTip_.setVisible(false);
               return;
            }
            
            // screen no completion ext
            CppCompletion completion = completions_.get(index);
            String text = completion.getText();
            if (text == null)
            {
               toolTip_.setVisible(false);
               return;
            }
            
            // set the tooltip text
            toolTip_.setText(text);
           
            // position it in the next event loop
            Scheduler.get().scheduleDeferred(new ScheduledCommand() {

               @Override
               public void execute()
               { 
                  // some constants
                  final int H_PAD = 12;
                  final int V_PAD = -3;
                  final int H_BUFFER = 100;
                  final int MIN_WIDTH = 300;
                  
                  // candidate left and top
                  final int left = selectedItem.getAbsoluteLeft() 
                             + selectedItem.getOffsetWidth() + H_PAD;
                  final int top = selectedItem.getAbsoluteTop() + V_PAD;
      
                  // do we have enough room to the right?
                  int roomRight = Window.getClientWidth() - left;
                  if (roomRight >= MIN_WIDTH)
                  {
                     if (toolTip_.getAbsoluteLeft() != left ||
                         toolTip_.getAbsoluteTop() != top)
                     {
                        // cap the width such that there are still H_BUFFER pixels
                        // of whitespace remaining
                        toolTip_.setMaxWidth(roomRight - H_BUFFER);
                        
                        toolTip_.setPopupPositionAndShow(new PositionCallback(){
   
                           @Override
                           public void setPosition(int offsetWidth,
                                                   int offsetHeight)
                           {
                              toolTip_.setPopupPosition(left, top);
                           }
                        });
                     }
                     else
                     {
                        if (!toolTip_.isVisible())
                           toolTip_.setVisible(true);
                     }

                  }
                  else
                  {
                     toolTip_.setVisible(false);
                  }
               }
            });
         }
      });
      
      addCloseHandler(new CloseHandler<PopupPanel>() {

         @Override
         public void onClose(CloseEvent<PopupPanel> event)
         {
            toolTip_.hide();
            toolTip_ = null;
         }
      });
   }
   
   @Override
   protected int getMaxHeight()
   {
      return 180;
   }
   
   private final DocDisplay docDisplay_;
   private final Position completionPosition_;
   private JsArray<CppCompletion> completions_;
   private CommandWithArg<CppCompletion> onSelected_ = null;
   private Boolean showBelow_ = null;
   private boolean updatingMenu_ = false;
   private CppCompletionToolTip toolTip_;
   
}
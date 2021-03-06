package org.technbolts.jbehave.eclipse.editors.story.actions;

import java.util.ResourceBundle;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.ui.texteditor.TextEditorAction;
import org.technbolts.jbehave.eclipse.Activator;
import org.technbolts.jbehave.eclipse.editors.story.StoryEditor;
import org.technbolts.jbehave.eclipse.editors.story.outline.QuickOutlinePopupDialog;

public class ShowOutlineAction extends TextEditorAction {

    public ShowOutlineAction(final ResourceBundle bundle, final String prefix,
            final ITextEditor editor) {
        super(bundle, prefix, editor);
    }

    @Override
    public void run() {
        if (getTextEditor() instanceof StoryEditor) {
            StoryEditor editor = (StoryEditor) getTextEditor();
            Shell parent = getTextEditor().getSite().getShell();
            QuickOutlinePopupDialog quickOutlinePopupDialog = new QuickOutlinePopupDialog(
                    parent, SWT.NONE, editor, Activator.getDefault().getImageRegistry());
            quickOutlinePopupDialog.open();
        }
    }
}

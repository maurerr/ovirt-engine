package org.ovirt.engine.ui.common.widget.action;

import javax.inject.Inject;

import org.gwtbootstrap3.client.ui.constants.IconType;
import org.ovirt.engine.core.common.businessentities.storage.Disk;
import org.ovirt.engine.ui.common.CommonApplicationConstants;
import org.ovirt.engine.ui.common.gin.AssetProvider;
import org.ovirt.engine.ui.common.presenter.DetailActionPanelPresenterWidget;
import org.ovirt.engine.ui.common.uicommon.model.SearchableDetailModelProvider;
import org.ovirt.engine.ui.uicommonweb.UICommand;
import org.ovirt.engine.ui.uicommonweb.models.vms.VmDiskListModel;
import org.ovirt.engine.ui.uicommonweb.models.vms.VmListModel;

import com.google.gwt.event.logical.shared.InitializeEvent;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.web.bindery.event.shared.EventBus;

public class VmDiskActionPanelPresenterWidget extends DetailActionPanelPresenterWidget<Disk, VmListModel<Void>, VmDiskListModel> {

    private static final CommonApplicationConstants constants = AssetProvider.getConstants();

    private ImageUiCommandButtonDefinition<Disk> plugButtonDefinition;
    private ImageUiCommandButtonDefinition<Disk> unPlugButtonDefinition;

    private boolean showMoveButton = false;

    @Inject
    public VmDiskActionPanelPresenterWidget(EventBus eventBus,
            DetailActionPanelPresenterWidget.ViewDef<Disk> view,
            SearchableDetailModelProvider<Disk, VmListModel<Void>, VmDiskListModel> dataProvider) {
        super(eventBus, view, dataProvider);
    }

    @Override
    protected void initializeButtons() {
        addActionButton(new UiCommandButtonDefinition<Disk>(getSharedEventBus(), constants.newDisk()) {
            @Override
            protected UICommand resolveCommand() {
                return getDetailModel().getNewCommand();
            }
        });

        addActionButton(new UiCommandButtonDefinition<Disk>(getSharedEventBus(), constants.attachDisk()) {
            @Override
            protected UICommand resolveCommand() {
                return getDetailModel().getAttachCommand();
            }
        });

        addActionButton(new UiCommandButtonDefinition<Disk>(getSharedEventBus(), constants.editDisk()) {
            @Override
            protected UICommand resolveCommand() {
                return getDetailModel().getEditCommand();
            }
        });

        addActionButton(new UiCommandButtonDefinition<Disk>(getSharedEventBus(), constants.removeDisk()) {
            @Override
            protected UICommand resolveCommand() {
                return getDetailModel().getRemoveCommand();
            }
        });

        addMenuListItem(new UiCommandButtonDefinition<Disk>(getSharedEventBus(), constants.sparsifyDisk()) {
            @Override
            protected UICommand resolveCommand() {
                return getDetailModel().getSparsifyCommand();
            }
        });

        plugButtonDefinition = new ImageUiCommandButtonDefinition<Disk>(getSharedEventBus(), constants.activateDisk(),
                IconType.ARROW_UP, true, false) {
            @Override
            protected UICommand resolveCommand() {
                return getDetailModel().getPlugCommand();
            }

            @Override
            public SafeHtml getTooltip() {
                SafeHtml tooltip = null;
                if (getDetailModel().isHotPlugAvailable() && !getDetailModel().isPlugAvailableByDisks(true)) {
                    tooltip = SafeHtmlUtils.fromString(constants.diskHotPlugNotSupported());
                }
                return tooltip;
            }
        };
        addMenuListItem(plugButtonDefinition);

        unPlugButtonDefinition = new ImageUiCommandButtonDefinition<Disk>(getSharedEventBus(), constants.deactivateDisk(),
                IconType.ARROW_DOWN, true, false) {
            @Override
            protected UICommand resolveCommand() {
                return getDetailModel().getUnPlugCommand();
            }

            @Override
            public SafeHtml getTooltip() {
                SafeHtml tooltip = null;
                if (getDetailModel().isHotPlugAvailable() && !getDetailModel().isPlugAvailableByDisks(false)) {
                    tooltip = SafeHtmlUtils.fromString(constants.diskHotPlugNotSupported());
                }
                return tooltip;
            }
        };
        addMenuListItem(unPlugButtonDefinition);

        attachActivationListenersForModel();

        if (showMoveButton) {
            addMenuListItem(new UiCommandButtonDefinition<Disk>(getSharedEventBus(), constants.moveDisk()) {
                @Override
                protected UICommand resolveCommand() {
                    return getDetailModel().getMoveCommand();
                }
            });
        }

        addMenuListItem(new UiCommandButtonDefinition<Disk>(getSharedEventBus(), constants.getDiskAlignment(),
                CommandLocation.OnlyFromContext) {
            @Override
            protected UICommand resolveCommand() {
                return getDetailModel().getScanAlignmentCommand();
            }
        });

        addMenuListItem(new UiCommandButtonDefinition<Disk>(getSharedEventBus(), constants.assignQuota()) {
            @Override
            protected UICommand resolveCommand() {
                return getDetailModel().getChangeQuotaCommand();
            }
        });
    }

    protected void attachActivationListenersForModel() {
        getDetailModel().getPropertyChangedEvent().addListener((ev, sender, args) -> {
            if ("IsDiskHotPlugAvailable".equals(args.propertyName)) { //$NON-NLS-1$
                InitializeEvent.fire(plugButtonDefinition);
                InitializeEvent.fire(unPlugButtonDefinition);
            }
        });

        getDetailModel().getSelectedItemChangedEvent().addListener((ev, sender, args) -> {
            InitializeEvent.fire(plugButtonDefinition);
            InitializeEvent.fire(unPlugButtonDefinition);
        });
    }

    public void setShowMoveButton(boolean value) {
        this.showMoveButton = value;
    }
}

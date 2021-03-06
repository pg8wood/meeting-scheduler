package com.mycompany.jsfClasses;

import com.mycompany.entityClasses.Meeting;
import com.mycompany.entityClasses.MeetingUsers;
import com.mycompany.entityClasses.User;
import com.mycompany.jsfClasses.util.JsfUtil;
import com.mycompany.jsfClasses.util.PaginationHelper;
import com.mycompany.sessionBeans.MeetingUsersFacade;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.ResourceBundle;
import javax.ejb.EJB;
import javax.inject.Named;
import javax.enterprise.context.SessionScoped;
import javax.faces.application.FacesMessage;
import javax.faces.component.UIComponent;
import javax.faces.context.FacesContext;
import javax.faces.convert.Converter;
import javax.faces.convert.FacesConverter;
import javax.faces.model.DataModel;
import javax.faces.model.ListDataModel;
import javax.faces.model.SelectItem;

@Named("meetingUsersController")
@SessionScoped
public class MeetingUsersController implements Serializable {

    private MeetingUsers current;
    private DataModel items = null;
    @EJB
    private com.mycompany.sessionBeans.MeetingUsersFacade ejbFacade;
    private PaginationHelper pagination;
    private int selectedItemIndex;

    public MeetingUsersController() {
    }

    public MeetingUsers getSelected() {
        if (current == null) {
            current = new MeetingUsers();
            current.setMeetingUsersPK(new com.mycompany.entityClasses.MeetingUsersPK());
            selectedItemIndex = -1;
        }
        return current;
    }

    public void setSelected(User user, Meeting meeting) {
        current = ejbFacade.getMeetingUser(user, meeting);
        current.setUser(user);
        current.setMeeting(meeting);
        System.out.println("current meetingusers object: " + current.toString() + "meeting: " + current.getMeeting().toString());
    }

    private MeetingUsersFacade getFacade() {
        return ejbFacade;
    }

    public PaginationHelper getPagination() {
        if (pagination == null) {
            pagination = new PaginationHelper(10) {

                @Override
                public int getItemsCount() {
                    return getFacade().count();
                }

                @Override
                public DataModel createPageDataModel() {
                    return new ListDataModel(getFacade().findRange(new int[]{getPageFirstItem(), getPageFirstItem() + getPageSize()}));
                }
            };
        }
        return pagination;
    }

    public String prepareList() {
        recreateModel();
        return "List";
    }

    public String prepareView() {
        current = (MeetingUsers) getItems().getRowData();
        selectedItemIndex = pagination.getPageFirstItem() + getItems().getRowIndex();
        return "View";
    }

    public String prepareCreate() {
        current = new MeetingUsers();
        current.setMeetingUsersPK(new com.mycompany.entityClasses.MeetingUsersPK());
        selectedItemIndex = -1;
        return "Create";
    }

    public String create() {
        try {
            current.getMeetingUsersPK().setMeetingId(current.getMeeting().getId());
            current.getMeetingUsersPK().setUserId(current.getUser().getId());
            getFacade().create(current);
            return prepareCreate();
        } catch (Exception e) {
            JsfUtil.addErrorMessage(e, ResourceBundle.getBundle("/Bundle").getString("PersistenceErrorOccured"));
            return null;
        }
    }

    public String prepareEdit() {
        current = (MeetingUsers) getItems().getRowData();
        selectedItemIndex = pagination.getPageFirstItem() + getItems().getRowIndex();
        return "Edit";
    }

    public String update() {
        try {
            System.out.println("current meetingusers for update: " + current.toString());
            System.out.println("current meetingusers meeting for update: " + current.getMeeting().toString());
            System.out.println("current meetingusers meeting ID for update: " + current.getMeeting().getId());
            
            
            current.getMeetingUsersPK().setMeetingId(current.getMeeting().getId());
            current.getMeetingUsersPK().setUserId(current.getUser().getId());
            performDestroy();
            create();
            JsfUtil.addSuccessMessage(ResourceBundle.getBundle("/Bundle").getString("MeetingUsersUpdated"));
            return "View";
        } catch (Exception e) {
            System.out.println("ERROR UPDATING. SEE");
            e.printStackTrace();
            
            JsfUtil.addErrorMessage(e, ResourceBundle.getBundle("/Bundle").getString("PersistenceErrorOccured"));
            return null;
        }
    }

    public String destroy() {
        current = (MeetingUsers) getItems().getRowData();
        selectedItemIndex = pagination.getPageFirstItem() + getItems().getRowIndex();
        performDestroy();
        recreatePagination();
        recreateModel();
        return "List";
    }

    public String destroyAndView() {
        performDestroy();
        recreateModel();
        updateCurrentItem();
        if (selectedItemIndex >= 0) {
            return "View";
        } else {
            // all items were removed - go back to list
            recreateModel();
            return "List";
        }
    }

    private void performDestroy() {
        try {
            getFacade().remove(current);
        } catch (Exception e) {
            JsfUtil.addErrorMessage(e, ResourceBundle.getBundle("/Bundle").getString("PersistenceErrorOccured"));
        }
    }

    private void updateCurrentItem() {
        int count = getFacade().count();
        if (selectedItemIndex >= count) {
            // selected index cannot be bigger than number of items:
            selectedItemIndex = count - 1;
            // go to previous page if last page disappeared:
            if (pagination.getPageFirstItem() >= count) {
                pagination.previousPage();
            }
        }
        if (selectedItemIndex >= 0) {
            current = getFacade().findRange(new int[]{selectedItemIndex, selectedItemIndex + 1}).get(0);
        }
    }

    public DataModel getItems() {
        if (items == null) {
            items = getPagination().createPageDataModel();
        }
        return items;
    }

    private void recreateModel() {
        items = null;
    }

    private void recreatePagination() {
        pagination = null;
    }

    public String next() {
        getPagination().nextPage();
        recreateModel();
        return "List";
    }

    public String previous() {
        getPagination().previousPage();
        recreateModel();
        return "List";
    }

    public SelectItem[] getItemsAvailableSelectMany() {
        return JsfUtil.getSelectItems(ejbFacade.findAll(), false);
    }

    public SelectItem[] getItemsAvailableSelectOne() {
        return JsfUtil.getSelectItems(ejbFacade.findAll(), true);
    }

    public MeetingUsers getMeetingUsers(com.mycompany.entityClasses.MeetingUsersPK id) {
        return ejbFacade.find(id);
    }

    /**
     * Saves a user's indicated available  times and sends an email to the 
     * meeting owner. 
     * 
     * @param availability
     * @param user
     * @param ownerEmail
     * @throws Exception if an error occurred 
     */
    public void finalizeMeetingAvailability(ArrayList<String> availability, User user, String ownerEmail) throws Exception {
        String finalTime;
        if (availability.isEmpty()) {
            finalTime = "";
        } else {
            finalTime = String.join(", ", availability);
        }

        System.out.println("User availability string set to " + finalTime);

        // Update the growl message
        FacesContext context = FacesContext.getCurrentInstance();

        current.setAvailableTimes(finalTime);
        current.setResponse(true);
        update();
        
        // sends email for a new meeting response
        user.prepareOwnerEmail(1, ownerEmail);
    }

    @FacesConverter(forClass = MeetingUsers.class)
    public static class MeetingUsersControllerConverter implements Converter {

        private static final String SEPARATOR = "#";
        private static final String SEPARATOR_ESCAPED = "\\#";

        @Override
        public Object getAsObject(FacesContext facesContext, UIComponent component, String value) {
            if (value == null || value.length() == 0) {
                return null;
            }
            MeetingUsersController controller = (MeetingUsersController) facesContext.getApplication().getELResolver().
                    getValue(facesContext.getELContext(), null, "meetingUsersController");
            return controller.getMeetingUsers(getKey(value));
        }

        com.mycompany.entityClasses.MeetingUsersPK getKey(String value) {
            com.mycompany.entityClasses.MeetingUsersPK key;
            String values[] = value.split(SEPARATOR_ESCAPED);
            key = new com.mycompany.entityClasses.MeetingUsersPK();
            key.setUserId(Integer.parseInt(values[0]));
            key.setMeetingId(Integer.parseInt(values[1]));
            return key;
        }

        String getStringKey(com.mycompany.entityClasses.MeetingUsersPK value) {
            StringBuilder sb = new StringBuilder();
            sb.append(value.getUserId());
            sb.append(SEPARATOR);
            sb.append(value.getMeetingId());
            return sb.toString();
        }

        @Override
        public String getAsString(FacesContext facesContext, UIComponent component, Object object) {
            if (object == null) {
                return null;
            }
            if (object instanceof MeetingUsers) {
                MeetingUsers o = (MeetingUsers) object;
                return getStringKey(o.getMeetingUsersPK());
            } else {
                throw new IllegalArgumentException("object " + object + " is of type " + object.getClass().getName() + "; expected type: " + MeetingUsers.class.getName());
            }
        }

    }

}

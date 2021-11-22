package fr.polytech.conception.r1.session;

import org.checkerframework.checker.nullness.qual.NonNull;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Stream;

import fr.polytech.conception.r1.InvalidSessionDataException;
import fr.polytech.conception.r1.Notification;
import fr.polytech.conception.r1.Sport;
import fr.polytech.conception.r1.Util;
import fr.polytech.conception.r1.profile.User;

public class SessionOneshot extends Session implements Comparable<SessionOneshot>
{
    private final List<User> participants = new LinkedList<>();
    protected boolean isSponsored = false;
    private ZonedDateTime start;
    private ZonedDateTime end;
    private ZonedDateTime entryDeadline;
    private boolean isCancelled = false;
    private double sponsoredSessionPrice = 0d;

    public SessionOneshot(ZonedDateTime start, ZonedDateTime end, String address, Sport sport, User organizer, boolean isSponsored) throws InvalidSessionDataException
    {
        super(address, sport, organizer);
        checkDatesOrder(end, start);
        this.start = start;
        this.end = end;
        this.entryDeadline = start;
        if (Util.filterType(organizer.getOrganizedSessions().stream(), SessionOneshot.class).anyMatch(session ->
                session != this && Util.intersect(start, end, session)))
            throw new InvalidSessionDataException("Création de 2 sessions se déroulant au même moment");
        setSponsored(isSponsored);
    }

    public ZonedDateTime getStart()
    {
        return start;
    }

    public void setStart(ZonedDateTime start) throws InvalidSessionDataException
    {
        checkDatesOrder(end, start);
        this.start = start;
        notifyEdit();
    }

    public ZonedDateTime getEnd()
    {
        return end;
    }

    public void setEnd(ZonedDateTime end) throws InvalidSessionDataException
    {
        checkDatesOrder(end, start);
        this.end = end;
        notifyEdit();
    }

    public ZonedDateTime getEntryDeadline()
    {
        return entryDeadline;
    }

    public void setEntryDeadline(ZonedDateTime entryDeadline) throws InvalidSessionDataException
    {
        if (entryDeadline.isAfter(start))
        {
            throw new InvalidSessionDataException("La date limite d'inscription doit etre avant le debut de la seance");
        }
        this.entryDeadline = entryDeadline;
        notifyEdit();
    }

    public boolean isCancelled()
    {
        return isCancelled;
    }

    public void setCancelled(boolean cancelled)
    {
        this.isCancelled = cancelled;
        notifyCancel();
    }

    public List<User> getParticipants()
    {
        return participants;
    }

    //todo add notif
    public void participer(User participant) throws InvalidSessionDataException
    {
        if (participants.contains(participant))
        {
            throw new InvalidSessionDataException("Vous participez deja a cette session.");
        }
        if (maxParticipants != 0 && participants.size() >= maxParticipants) // maxParticipants = 0 -> infinite
        {
            throw new InvalidSessionDataException("Il y a deja trop de participants a cette session.");
        }
        if (friendsOnly && !organizer.getFriends().contains(participant))
        {
            throw new InvalidSessionDataException("Cette session est reservee aux amis de l'organisateur, vous n'en faites pas partie.");
        }
        if (organizer.haveIBlacklistedUser(participant))
        {
            throw new InvalidSessionDataException("T'es blacklist bro");
        }
        if (this.getStart().isBefore(ZonedDateTime.now()))
        {
            throw new InvalidSessionDataException("Cannot participate a passed session");
        }
        this.participants.add(participant);
    }

    @Override
    public void setMaxParticipants(int maxParticipants) throws InvalidSessionDataException
    {
        if (participants.size() > maxParticipants)
        {
            throw new InvalidSessionDataException("There are already " + participants.size() + " participants for this session");
        }
        super.setMaxParticipants(maxParticipants);
        notifyEdit();
    }

    @Override
    public int compareTo(@NonNull SessionOneshot other)
    {
        ZonedDateTime now = ZonedDateTime.now();
        if (now.isAfter(this.start) || now.isAfter(other.getStart()))
        {
            return 0;
        }
        double daysBeforeThisSession = (Duration.between(now, this.start).getSeconds() / 86400d) + 1d;
        double daysBeforeOtherSession = (Duration.between(now, other.getStart()).getSeconds() / 86400d) + 1d;
        double thisValue = 1d / (2d * daysBeforeThisSession) + (isSponsored ? 0.5 : 0);
        double otherValue = 1d / (2d * daysBeforeOtherSession) + (other.isSponsored() ? 0.5 : 0);
        double difference = otherValue - thisValue;
        while (difference < 10d && difference > -10d)
        {
            difference = difference * 10d;
        }
        return (int) difference;
    }

    //todo notif
    public boolean excludeUser(User user)
    {
        boolean r = participants.contains(user) && user.getAttendedSessions().contains(this);
        participants.remove(user);
        user.getAttendedSessions().remove(this);
        return r;
    }

    @Override
    public Stream<? extends SessionOneshot> getOneshots(ZonedDateTime end)
    {
        return Stream.of(this);
    }

    public boolean isSponsored()
    {
        return isSponsored;
    }

    public void setSponsored(boolean sponsored) throws InvalidSessionDataException
    {
        if (sponsored && !organizer.isSpecialUser())
        {
            throw new InvalidSessionDataException("Only special users can organise sponsored session");
        }
        isSponsored = sponsored;
        notifyEdit();
    }

    public double getPrice()
    {
        return sponsoredSessionPrice;
    }

    public void setPrice(double price) throws InvalidSessionDataException
    {
        if (!isSponsored)
        {
            throw new InvalidSessionDataException("Cannot set price of unsponsored session");
        }
        sponsoredSessionPrice = price;
    }

    private void notifyParticipants(Notification notification)
    {
        participants.forEach(user -> user.notify(notification));
    }

    private void notifyEdit()
    {
        notifyParticipants(new SessionEditedNotification(this));
    }

    private void notifyCancel()
    {
        notifyParticipants(new SessionCancelNotification(this));
    }

    private void notifyOrganizer(Notification notification)
    {
        organizer.notify(notification);
    }

    //todo call with unsubscribe
    private void notifyCancelParticipation(User user)
    {
        notifyOrganizer(new SessionCancelParticipationNotification(this, user));
    }

    public static class SessionEditedNotification extends Notification
    {
        private final SessionOneshot sessionOneshot;

        public SessionEditedNotification(SessionOneshot sessionOneshot)
        {
            super();
            this.sessionOneshot = sessionOneshot;
        }

        public SessionOneshot getSessionOneshot()
        {
            return sessionOneshot;
        }

        @Override
        public String getMessage()
        {
            return "session modif";
        }
    }

    private static class SessionCancelNotification extends Notification
    {
        private final SessionOneshot sessionOneshot;

        public SessionCancelNotification(SessionOneshot sessionOneshot)
        {
            super();
            this.sessionOneshot = sessionOneshot;
        }

        public SessionOneshot getSessionOneshot()
        {
            return sessionOneshot;
        }

        @Override
        public String getMessage()
        {
            return "session cancel";
        }
    }

    private class SessionCancelParticipationNotification extends Notification
    {
        private final SessionOneshot sessionOneshot;
        private final User user;
        public SessionCancelParticipationNotification(SessionOneshot sessionOneshot, User user)
        {
            super();
            this.sessionOneshot = sessionOneshot;
            this.user = user;
        }

        public SessionOneshot getSessionOneshot()
        {
            return sessionOneshot;
        }

        @Override
        public String getMessage()
        {
            return "user canceled their participation";
        }
    }
}

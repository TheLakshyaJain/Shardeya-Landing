import React, { useState } from 'react';
import { 
  CalendarDays, Plus, Clock, MapPin, Phone, 
  CheckCircle2, AlertCircle, MessageSquare, 
  Calendar, Filter, ChevronLeft, ChevronRight, X
} from 'lucide-react';
import { useCrm } from '../../../../context/CrmContext';
import { useLanguage } from '../../../../context/LanguageContext';
import { CalendarEvent } from '../../../../types/crm';

export const CalendarPage: React.FC = () => {
  const { calendarEvents, addCalendarEvent, projects } = useCrm();
  const { language } = useLanguage();
  const isHi = language === 'hi';

  const [selectedType, setSelectedType] = useState<string>('ALL');
  const [isAddModalOpen, setIsAddModalOpen] = useState(false);

  // New Event Form State
  const [newEvent, setNewEvent] = useState({
    title: '',
    eventDate: new Date().toISOString().split('T')[0],
    eventTime: '11:00 AM',
    eventType: 'SITE_VISIT' as CalendarEvent['eventType'],
    entityName: '',
    contactNumber: '',
    assignedTo: 'Rajeshwar Singhania',
    status: 'SCHEDULED' as const
  });

  const eventTypes: { type: CalendarEvent['eventType']; label: string; color: string }[] = [
    { type: 'SITE_VISIT', label: isHi ? 'साइट विजिट' : 'Site Visit', color: 'bg-emerald-100 text-emerald-800 border-emerald-300' },
    { type: 'REGISTRY', label: isHi ? 'रजिस्ट्री निष्पादन' : 'Registry Deed', color: 'bg-purple-100 text-purple-800 border-purple-300' },
    { type: 'INSTALMENT_DUE', label: isHi ? 'किस्त देय फॉलो-अप' : 'Payment Due', color: 'bg-amber-100 text-amber-800 border-amber-300' },
    { type: 'FOLLOW_UP', label: isHi ? 'क्लाइंट फॉलो-अप' : 'Client Call', color: 'bg-blue-100 text-blue-800 border-blue-300' }
  ];

  const filteredEvents = calendarEvents.filter(ev => {
    if (selectedType !== 'ALL' && ev.eventType !== selectedType) return false;
    return true;
  }).sort((a, b) => new Date(a.eventDate).getTime() - new Date(b.eventDate).getTime());

  const handleCreateEvent = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newEvent.title || !newEvent.entityName) return;

    addCalendarEvent(newEvent);
    setIsAddModalOpen(false);
    setNewEvent({
      title: '',
      eventDate: new Date().toISOString().split('T')[0],
      eventTime: '11:00 AM',
      eventType: 'SITE_VISIT',
      entityName: '',
      contactNumber: '',
      assignedTo: 'Rajeshwar Singhania',
      status: 'SCHEDULED'
    });
  };

  const openWhatsAppNotice = (ev: CalendarEvent) => {
    const text = encodeURIComponent(
      `*SHARDEYA REAL ESTATE NOTICE*\n\n` +
      `Namaste ${ev.entityName},\n` +
      `This is a confirmation reminder for your scheduled ${ev.eventType.replace('_', ' ')}:\n` +
      `📌 *Event:* ${ev.title}\n` +
      `📅 *Date & Time:* ${ev.eventDate} at ${ev.eventTime || '11:00 AM'}\n\n` +
      `Our executive (${ev.assignedTo}) will coordinate with you. For assistance, contact +91 98119 44332.`
    );
    window.open(`https://wa.me/${ev.contactNumber.replace(/[^0-9]/g, '')}?text=${text}`, '_blank');
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <span className="px-2.5 py-0.5 rounded-full text-xs font-semibold bg-forest/10 text-forest border border-forest/20 tracking-wide uppercase">
              {isHi ? 'शेड्यूल व साइट विजिट' : 'Operations Agenda'}
            </span>
          </div>
          <h1 className="text-2xl sm:text-3xl font-serif font-bold text-espresso-950 mt-1">
            {isHi ? 'साइट विजिट व रजिस्ट्री कैलेंडर' : 'Site Visits & Operations Agenda'}
          </h1>
          <p className="text-sm text-espresso-700">
            {isHi 
              ? 'क्लाइंट विजिट्स, तहसील रजिस्ट्री तारीखें एवं समयबद्ध पेमेंट फॉलो-अप्स।' 
              : 'Synchronized field visits, sub-registrar registry deeds, and collection follow-up appointments.'}
          </p>
        </div>

        <button
          onClick={() => setIsAddModalOpen(true)}
          className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl bg-forest hover:bg-forest-600 text-white font-medium shadow-sm transition-colors text-sm self-start sm:self-auto"
        >
          <Plus className="w-4 h-4" />
          {isHi ? 'नया अपॉइंटमेंट जोड़ें' : 'Schedule Appointment'}
        </button>
      </div>

      {/* Filter Chips */}
      <div className="flex items-center gap-2 overflow-x-auto pb-1">
        <button
          onClick={() => setSelectedType('ALL')}
          className={`px-3 py-1.5 rounded-lg text-xs font-semibold whitespace-nowrap transition-colors ${
            selectedType === 'ALL'
              ? 'bg-forest text-white'
              : 'bg-white border border-sand-300 text-espresso-700 hover:bg-sand-100'
          }`}
        >
          {isHi ? 'सभी अपॉइंटमेंट्स' : 'All Events'} ({calendarEvents.length})
        </button>
        {eventTypes.map(t => (
          <button
            key={t.type}
            onClick={() => setSelectedType(t.type)}
            className={`px-3 py-1.5 rounded-lg text-xs font-semibold whitespace-nowrap transition-colors ${
              selectedType === t.type
                ? 'bg-forest text-white'
                : 'bg-white border border-sand-300 text-espresso-700 hover:bg-sand-100'
            }`}
          >
            {t.label} ({calendarEvents.filter(e => e.eventType === t.type).length})
          </button>
        ))}
      </div>

      {/* Agenda Timeline List */}
      <div className="space-y-3">
        {filteredEvents.length === 0 ? (
          <div className="p-12 text-center bg-white border border-sand-300 rounded-xl">
            <CalendarDays className="w-10 h-10 text-espresso-300 mx-auto mb-3" />
            <h3 className="font-serif font-bold text-espresso-950">No events found</h3>
            <p className="text-xs text-espresso-600 mt-1">Schedule client site inspections or registry appointments.</p>
          </div>
        ) : (
          filteredEvents.map(event => {
            const isToday = event.eventDate === new Date().toISOString().split('T')[0];
            const badge = eventTypes.find(t => t.type === event.eventType);

            return (
              <div 
                key={event.id}
                className={`p-5 rounded-xl bg-white border transition-shadow hover:shadow-md flex flex-col md:flex-row md:items-center justify-between gap-4 ${
                  isToday ? 'border-forest/50 ring-1 ring-forest/20' : 'border-sand-300'
                }`}
              >
                <div className="flex items-start gap-4">
                  {/* Date Badge */}
                  <div className="w-16 h-16 rounded-xl bg-sand-100 border border-sand-200 flex flex-col items-center justify-center text-center shrink-0">
                    <span className="text-[10px] font-bold uppercase tracking-wider text-forest">
                      {new Date(event.eventDate).toLocaleDateString('en-IN', { month: 'short' })}
                    </span>
                    <span className="text-xl font-bold font-serif text-espresso-950 leading-none mt-0.5">
                      {new Date(event.eventDate).getDate()}
                    </span>
                    <span className="text-[10px] text-espresso-500 font-medium">
                      {new Date(event.eventDate).toLocaleDateString('en-IN', { weekday: 'short' })}
                    </span>
                  </div>

                  {/* Details */}
                  <div className="space-y-1">
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className={`px-2 py-0.5 rounded text-[11px] font-semibold border ${badge?.color}`}>
                        {badge?.label}
                      </span>
                      {isToday && (
                        <span className="px-2 py-0.5 rounded text-[11px] font-semibold bg-red-100 text-red-800 animate-pulse">
                          TODAY
                        </span>
                      )}
                      <span className="text-xs text-espresso-500 flex items-center gap-1 font-mono">
                        <Clock className="w-3.5 h-3.5" />
                        {event.eventTime || '11:00 AM'}
                      </span>
                    </div>

                    <h3 className="text-base font-serif font-bold text-espresso-950">
                      {event.title}
                    </h3>

                    <div className="flex items-center gap-4 text-xs text-espresso-600 flex-wrap">
                      <span className="font-medium text-espresso-900">Buyer / Party: {event.entityName}</span>
                      {event.contactNumber && (
                        <span className="flex items-center gap-1 font-mono">
                          <Phone className="w-3 h-3 text-espresso-400" />
                          {event.contactNumber}
                        </span>
                      )}
                      <span>Assigned to: <strong className="text-espresso-800">{event.assignedTo}</strong></span>
                    </div>
                  </div>
                </div>

                {/* Actions */}
                <div className="flex items-center gap-2 self-end md:self-center shrink-0">
                  {event.contactNumber && (
                    <button
                      onClick={() => openWhatsAppNotice(event)}
                      className="px-3 py-1.5 rounded-lg bg-emerald-50 hover:bg-emerald-100 text-emerald-800 border border-emerald-200 text-xs font-medium flex items-center gap-1.5 transition-colors"
                      title="Send WhatsApp Confirmation"
                    >
                      <MessageSquare className="w-3.5 h-3.5 text-emerald-600" />
                      WhatsApp Notice
                    </button>
                  )}
                  <span className="px-3 py-1 rounded-lg bg-sand-100 text-xs font-semibold text-espresso-700">
                    {event.status}
                  </span>
                </div>
              </div>
            );
          })
        )}
      </div>

      {/* Schedule Appointment Modal */}
      {isAddModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-espresso-950/60 backdrop-blur-sm p-4">
          <div className="w-full max-w-md bg-white rounded-2xl shadow-2xl border border-sand-300 overflow-hidden">
            <div className="p-5 bg-[#0B1411] text-white flex items-center justify-between">
              <div>
                <span className="text-xs text-emerald-400 font-mono uppercase tracking-wider">Calendar Entry</span>
                <h3 className="text-lg font-serif font-bold mt-0.5">Schedule Appointment</h3>
              </div>
              <button 
                onClick={() => setIsAddModalOpen(false)}
                className="p-1 rounded-lg text-white/70 hover:text-white hover:bg-white/10"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleCreateEvent} className="p-6 space-y-4">
              <div>
                <label className="block text-xs font-semibold text-espresso-700 mb-1">Appointment Title *</label>
                <input
                  type="text"
                  required
                  placeholder="e.g. Site Visit Plot #104 with Dr. Harshvardhan"
                  value={newEvent.title}
                  onChange={e => setNewEvent(p => ({ ...p, title: e.target.value }))}
                  className="w-full px-3 py-2 text-sm bg-white border border-sand-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-forest/20 focus:border-forest"
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-espresso-700 mb-1">Date *</label>
                  <input
                    type="date"
                    required
                    value={newEvent.eventDate}
                    onChange={e => setNewEvent(p => ({ ...p, eventDate: e.target.value }))}
                    className="w-full px-3 py-2 text-sm bg-white border border-sand-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-forest/20 focus:border-forest font-mono"
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-espresso-700 mb-1">Time</label>
                  <input
                    type="text"
                    placeholder="11:00 AM"
                    value={newEvent.eventTime}
                    onChange={e => setNewEvent(p => ({ ...p, eventTime: e.target.value }))}
                    className="w-full px-3 py-2 text-sm bg-white border border-sand-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-forest/20 focus:border-forest"
                  />
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-espresso-700 mb-1">Event Category</label>
                <select
                  value={newEvent.eventType}
                  onChange={e => setNewEvent(p => ({ ...p, eventType: e.target.value as CalendarEvent['eventType'] }))}
                  className="w-full px-3 py-2 text-sm bg-white border border-sand-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-forest/20 focus:border-forest"
                >
                  <option value="SITE_VISIT">Site Visit / Physical Plot Tour</option>
                  <option value="REGISTRY">Registry Deed Execution (Sub-Registrar)</option>
                  <option value="INSTALMENT_DUE">Instalment Due Follow-Up</option>
                  <option value="FOLLOW_UP">Client Negotiation Call</option>
                </select>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-espresso-700 mb-1">Client Name *</label>
                  <input
                    type="text"
                    required
                    placeholder="e.g. Vikrant Goel"
                    value={newEvent.entityName}
                    onChange={e => setNewEvent(p => ({ ...p, entityName: e.target.value }))}
                    className="w-full px-3 py-2 text-sm bg-white border border-sand-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-forest/20 focus:border-forest"
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-espresso-700 mb-1">Mobile Number</label>
                  <input
                    type="tel"
                    placeholder="+91 98111 22334"
                    value={newEvent.contactNumber}
                    onChange={e => setNewEvent(p => ({ ...p, contactNumber: e.target.value }))}
                    className="w-full px-3 py-2 text-sm bg-white border border-sand-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-forest/20 focus:border-forest font-mono"
                  />
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-espresso-700 mb-1">Assigned Executive</label>
                <input
                  type="text"
                  value={newEvent.assignedTo}
                  onChange={e => setNewEvent(p => ({ ...p, assignedTo: e.target.value }))}
                  className="w-full px-3 py-2 text-sm bg-white border border-sand-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-forest/20 focus:border-forest"
                />
              </div>

              <div className="flex items-center gap-3 pt-3">
                <button
                  type="button"
                  onClick={() => setIsAddModalOpen(false)}
                  className="w-1/2 py-2.5 rounded-xl border border-sand-300 text-espresso-700 text-sm font-medium hover:bg-sand-100 transition-colors"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="w-1/2 py-2.5 rounded-xl bg-forest hover:bg-forest-600 text-white text-sm font-semibold shadow-sm transition-colors"
                >
                  Save Entry
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

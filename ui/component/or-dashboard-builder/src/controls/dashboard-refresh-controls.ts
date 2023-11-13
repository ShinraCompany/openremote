import {InputType} from "@openremote/or-mwc-components/or-mwc-input";
import {ListItem} from "@openremote/or-mwc-components/or-mwc-list";
import {getContentWithMenuTemplate} from "@openremote/or-mwc-components/or-mwc-menu";
import {html, LitElement, PropertyValues, TemplateResult} from "lit";
import {customElement, property} from "lit/decorators.js";
import {when} from "lit/directives/when.js";
import {i18next} from "@openremote/or-translate";
import {DashboardRefreshInterval} from "@openremote/model";

export function intervalToMillis(interval: DashboardRefreshInterval): number | undefined {
    switch (interval) {
        case DashboardRefreshInterval.OFF:
            return undefined;
        case DashboardRefreshInterval.TEN_SECONDS:
            return (10 * 1000);
        case DashboardRefreshInterval.ONE_MIN:
            return (60 * 1000);
        case DashboardRefreshInterval.FIVE_MIN:
            return (5 * 60 * 1000);
        case DashboardRefreshInterval.QUARTER:
            return (15 * 60 * 1000);
        case DashboardRefreshInterval.ONE_HOUR:
            return (60 * 60 * 1000);
        default:
            return undefined;
    }
}

export class IntervalSelectEvent extends CustomEvent<DashboardRefreshInterval> {

    public static readonly NAME = "interval-select";

    constructor(interval: DashboardRefreshInterval) {
        super(IntervalSelectEvent.NAME, {
            bubbles: true,
            composed: true,
            detail: interval
        });
    }
}

@customElement("dashboard-refresh-controls")
export class DashboardRefreshControls extends LitElement {

    @property()
    protected interval: DashboardRefreshInterval = DashboardRefreshInterval.OFF;

    @property()
    protected readonly = true;

    // TODO: Replace this with Object.values(), after generated typescript enums support this. ('const' variable issue)
    protected intervalOptions = [DashboardRefreshInterval.OFF, DashboardRefreshInterval.TEN_SECONDS, DashboardRefreshInterval.ONE_MIN, DashboardRefreshInterval.FIVE_MIN, DashboardRefreshInterval.QUARTER, DashboardRefreshInterval.ONE_HOUR]

    protected willUpdate(changedProps: PropertyValues) {
        super.willUpdate(changedProps);
        if (changedProps.has("interval") && this.interval !== undefined) {
            this.dispatchEvent(new IntervalSelectEvent(this.interval));
        }
    }

    protected render(): TemplateResult {
        const intervalOptions: string[] = this.getRefreshOptions();
        const value = this.getIntervalString(this.interval);
        console.log(intervalOptions);
        console.log(value);
        return html`
            <div style="height: 100%; display: flex; align-items: center;">
                ${when(this.readonly, () => html`
                    <or-mwc-input .type="${InputType.BUTTON}" .label="${i18next.t(value)}" disabled="true"></or-mwc-input>
                `, () => html`
                    ${getContentWithMenuTemplate(
                            html`<or-mwc-input .type="${InputType.BUTTON}" .label="${i18next.t(value)}"></or-mwc-input>`,
                            intervalOptions.map(o => ({value: o} as ListItem)),
                            value,
                            (newVal) => this.onIntervalSelect(intervalOptions, newVal as string),
                            undefined,
                            false,
                            true,
                            true
                    )}
                `)}
            </div>
        `;
    }

    protected onIntervalSelect(stringOptions: string[], value: string) {
        this.interval = this.intervalOptions[stringOptions.indexOf(value as DashboardRefreshInterval)];
    }

    protected getIntervalString(interval: DashboardRefreshInterval): string {
        return (`dashboard.interval.${interval.toLowerCase()}`);
    }

    protected getRefreshOptions(): string[] {
        return this.intervalOptions.map(interval => this.getIntervalString(interval));
    }
}

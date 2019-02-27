import 'ag-grid-community/dist/styles/ag-grid.css';
import 'ag-grid-community/dist/styles/ag-theme-material.css';
import { AgGridReact } from 'ag-grid-react';
import * as React from 'react';
import { connect } from 'react-redux';
import { RouteComponentProps } from 'react-router';
import { Link } from 'react-router-dom';
import ModelRecord from '../../models/ModelRecord';
import { fetchExperimentRuns } from '../../store/experiment-runs';
import { IApplicationState, IConnectedReduxProps } from '../../store/store';

import loader from '../images/loader.gif';
import styles from './ExperimentRuns.module.css';

import { FilterContextPool } from '../../models/FilterContextPool';
import { PropertyType } from '../../models/Filters';
import { columnDefinitions, defaultColDefinitions } from './columnDefinitions/Definitions';

const locationRegEx = /\/project\/[a-z0-9\-]+\/exp-runs/gim;
FilterContextPool.registerContext({
  metadata: [{ propertyName: 'Name', type: PropertyType.STRING }, { propertyName: 'Tag', type: PropertyType.STRING }],

  isFilteringSupport: true,
  isValidLocation: (location: string) => {
    return locationRegEx.test(location);
  },
  name: ModelRecord.name,
  onApplyFilters: (filters, dispatch) => {
    dispatch(fetchExperimentRuns('6a95fea8-5167-4046-ab0c-ef44ce229a78', filters));
  },
  onSearch: (text: string, dispatch) => {
    dispatch(
      fetchExperimentRuns('6a95fea8-5167-4046-ab0c-ef44ce229a78', [
        {
          invert: false,
          name: 'Name',
          type: PropertyType.STRING,
          value: text
        }
      ])
    );
  }
});

export interface IUrlProps {
  projectId: string;
}

interface IPropsFromState {
  data?: ModelRecord[] | null;
  loading: boolean;
  columnDefinitions: any;
  defaultColDefinitions: any;
  filterState: { [index: string]: {} };
  filtered: boolean;
}

interface IOperator {
  '>': any;
  '<': any;
  [key: string]: any;
}

type AllProps = RouteComponentProps<IUrlProps> & IPropsFromState & IConnectedReduxProps;

class ExperimentRuns extends React.Component<AllProps> {
  public gridApi: any;
  public columnApi: any;
  public data: any;

  public constructor(props: AllProps) {
    super(props);
  }

  public callFilterUpdate = () => {
    this.gridApi.onFilterChanged();
  };

  public componentWillReceiveProps() {
    if (this.gridApi !== undefined) {
      setTimeout(this.callFilterUpdate, 100);
    }
  }

  public componentDidUpdate() {
    if (this.props.data !== undefined && this.gridApi !== undefined) {
      this.gridApi.setRowData(this.props.data);
    }
  }
  public render() {
    const { data, loading } = this.props;

    return loading ? (
      <img src={loader} className={styles.loader} />
    ) : data ? (
      <div>
        <div className={`ag-theme-material ${styles.aggrid_wrapper}`}>
          <AgGridReact
            pagination={true}
            onGridReady={this.onGridReady}
            animateRows={true}
            getRowHeight={this.gridRowHeight}
            columnDefs={this.props.columnDefinitions}
            rowData={undefined}
            domLayout="autoHeight"
            defaultColDef={this.props.defaultColDefinitions}
            isExternalFilterPresent={this.isExternalFilterPresent}
            doesExternalFilterPass={this.doesExternalFilterPass}
          />
        </div>
      </div>
    ) : (
      ''
    );
  }

  public onGridReady = (params: any) => {
    this.gridApi = params.api;
    this.columnApi = params.columnApi;
    this.gridApi.setRowData(this.props.data);
  };

  public gridRowHeight = (params: any) => {
    const data = params.node.data;
    if (data.Metrics.length > 3 || data.Hyperparameters.length > 3) {
      if (data.Metrics.length > data.Hyperparameters.length) {
        return (data.Metric.length - 3) * 25 + 240;
      }
      return (data.Hyperparameters.length - 3) * 25 + 240;
    }
    if (data.tags.length >= 1) {
      return 220;
    }
    return 200;
  };

  public isExternalFilterPresent = () => {
    return this.props.filtered;
  };

  public funEvaluate(filter: any) {
    // this.data is from the bind(node) where node is table row data
    // **ts forced creation of public data var to be able access node
    const operators: IOperator = {
      '<': (a: number, b: number) => a < b,
      '>': (a: number, b: number) => a > b
    };
    switch (filter.type) {
      case 'tag':
        return this.data.tags.includes(filter.key);
      case 'param':
        return this.data[filter.subtype].find((params: any) => params.key === filter.param)
          ? operators[filter.operator](
              Number(
                this.data[filter.subtype].find((params: any) => {
                  if (params.key === filter.param) {
                    return params.value;
                  }
                }).value
              ),
              Number(filter.value)
            )
          : false;
      default:
        return true;
    }
  }

  public doesExternalFilterPass = (node: any) => {
    return Object.values(this.props.filterState)
      .map(this.funEvaluate.bind(node))
      .every(val => val === true);
  };

  public componentDidMount() {
    this.props.dispatch(fetchExperimentRuns(this.props.match.params.projectId));
  }
}

// filterState and filtered should be provided by from IApplicationState -> customFilter
const mapStateToProps = ({ experimentRuns }: IApplicationState) => ({
  columnDefinitions,
  defaultColDefinitions,
  data: experimentRuns.data,
  loading: experimentRuns.loading,
  filterState: {},
  filtered: false
});

export default connect(mapStateToProps)(ExperimentRuns);
